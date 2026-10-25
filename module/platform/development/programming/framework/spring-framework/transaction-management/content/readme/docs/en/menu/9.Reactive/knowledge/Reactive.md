<a id="back-to-top"></a>

# Reactive Transaction Model

## Menu
- [Reactive Transaction Context: ThreadLocal vs Reactor Context](#transaction-reactive-context-model)
- [ReactiveTransactionManager Contract](#transaction-reactive-manager-contract)
- [Reactive @Transactional Method Contract](#transaction-reactive-method-contract)
- [Context Participation and TransactionalOperator Scope](#transaction-reactive-participation)
- [Error and Cancellation Semantics](#transaction-reactive-error-cancel)
- [Reactive Transaction vs R2DBC/Data-Access Mechanics](#transaction-reactive-resource-boundary)

## <a id="transaction-reactive-context-model">Reactive Transaction Context: ThreadLocal vs Reactor Context</a>

<details>
<summary>Click for details</summary>

Imperative Spring transactions traditionally bind transaction state and resources to the current thread. Reactive pipelines break the assumption that one logical operation stays on one thread, so reactive transaction management uses **Reactor Context** instead of `ThreadLocal` as the carrier of transactional state.

The mental model is subscriber-scoped context:

```text
subscription
  ↓
Reactor Context contains transaction state
  ↓
operators participate while the context is visible
  ↓
thread may change without losing the transaction context
```

This distinction explains why simply calling a reactive repository from an imperative `@Transactional` method does not magically create a reactive transaction, and why thread switches are not themselves the problem in a correctly composed reactive flow.

Keep the two models separate: `org.springframework.transaction.support.TransactionSynchronizationManager` manages imperative thread-bound state, while the reactive package has its own context-aware transaction synchronization infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-reactive-manager-contract">ReactiveTransactionManager Contract</a>

<details>
<summary>Click for details</summary>

`ReactiveTransactionManager` is the central strategy for reactive transactions. Instead of returning a `TransactionStatus` synchronously, it returns publishers: `getReactiveTransaction(...)` yields a `Mono<ReactiveTransaction>`, and commit/rollback operations return `Mono<Void>`.

That contract matters because transaction begin, commit, and rollback may themselves require non-blocking asynchronous work. A concrete manager decides how to acquire the underlying resource, how to bind it to the reactive transaction context, and which propagation/isolation features the backend can honor.

Application code should reason against the generic contract, but never assume every reactive manager has identical backend capabilities. Nested transactions, suspension, isolation levels, timeouts, and read-only behavior remain manager/resource dependent.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-reactive-method-contract">Reactive @Transactional Method Contract</a>

<details>
<summary>Click for details</summary>

For `@Transactional` to select reactive transaction semantics, the transaction manager must be reactive and the method must return a reactive type that participates in the reactive pipeline. The transaction exists around **subscription-time execution**, not around the Java act of constructing a `Mono` or `Flux` object.

```java
@Transactional
public Mono<Order> createOrder(Order order) {
    return orders.save(order)
        .flatMap(saved -> audit.save(saved.id()).thenReturn(saved));
}
```

Returning a plain value from a method associated with a `ReactiveTransactionManager` is not merely non-transactional; in Spring Framework 6.1 it is an invalid manager/method pairing and transaction interception fails with an `IllegalStateException`. Methods with regular or `void` return types need an imperative `PlatformTransactionManager`. Likewise, subscribing manually inside a reactive method starts separate execution that is no longer represented by the returned chain.

Rule of thumb: compose and return the pipeline; let the caller/framework subscribe.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-reactive-participation">Context Participation and TransactionalOperator Scope</a>

<details>
<summary>Click for details</summary>

Reactive work participates in a transaction when it executes inside the Reactor context established by Spring's transaction infrastructure. `TransactionalOperator` can wrap a whole upstream chain with operator style, or provide callback style when the transaction should cover only selected publishers.

```java
Mono<Void> flow = operator.execute(status ->
    account.debit(from, amount)
        .then(account.credit(to, amount))
).then();
```

Context can be lost when code escapes the composed chain: manual subscriptions, callback APIs that are not bridged into Reactor, or publishing work through another execution mechanism can leave the transaction scope.

Think in terms of **pipeline membership**, not thread identity. If an operation is not part of the returned/composed publisher chain that carries the transaction context, it should not be assumed to participate.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-reactive-error-cancel">Error and Cancellation Semantics</a>

<details>
<summary>Click for details</summary>

Reactive transaction outcome follows reactive terminal signals. An error normally drives rollback according to the transaction policy; successful completion drives commit. Cancellation is more subtle because a canceled publisher has not produced a normal successful completion.

Spring's `TransactionalOperator` documentation explicitly calls out cancel signals because downstream operators such as `take`, `next`, or client disconnects can cancel upstream work. Since Spring Framework 5.3, a cancel signal causes rollback for a transactional publisher. Cancellation can therefore terminate transactional work before the application observes all emitted values.

For multi-value publishers, the consuming code should normally allow the transactional publisher to complete instead of canceling it after only part of the sequence has been consumed. Do not design a transaction whose correctness depends on draining an unbounded or long-lived `Flux`; for work that must complete atomically, prefer a finite pipeline with an unambiguous terminal outcome and test cancellation behavior when operators can short-circuit the source.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-reactive-resource-boundary">Reactive Transaction vs R2DBC/Data-Access Mechanics</a>

<details>
<summary>Click for details</summary>

Reactive transaction management coordinates transaction policy; it does not teach the data-access protocol itself. Spring R2DBC's `DatabaseClient`, connection factories, SQL mapping, and driver behavior belong to the data-access layer. This module only needs enough of that layer to explain participation.

The boundary is:

```text
transaction-management
→ when the reactive transaction starts, joins, commits, or rolls back

data-access / R2DBC
→ how SQL is sent, rows are mapped, and connections behave
```

Reactive transaction guarantees are also limited by the resource manager. A transaction over one R2DBC connection does not automatically make remote HTTP calls, Kafka publication, or another unrelated resource atomic. Those require explicit coordination patterns or a transaction technology capable of the required resources.

</details>

- [Back to top](#back-to-top)
