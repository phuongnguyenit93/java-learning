<a id="back-to-top"></a>

# Spring Transaction Abstraction and Manager Strategies

## Menu
- [TransactionManager as a Strategy Abstraction](#transaction-manager-strategy)
- [PlatformTransactionManager and Imperative Transactions](#transaction-platform-manager)
- [ReactiveTransactionManager and Reactive Transactions](#transaction-reactive-manager)
- [TransactionDefinition and Transaction Policy](#transaction-definition)
- [TransactionStatus and Transaction Execution State](#transaction-status)
- [Begin, Commit, Rollback, and Completion](#transaction-lifecycle)
- [Choosing and Qualifying a Transaction Manager](#transaction-manager-selection)
- [Local Resource Transactions vs Coordinated Transactions](#transaction-local-vs-global)

## <a id="transaction-manager-strategy">TransactionManager as a Strategy Abstraction</a>

<details>
<summary>Click for details</summary>

`TransactionManager` is the common marker for Spring transaction-manager implementations. It lets framework infrastructure refer to “a transaction manager” without assuming whether execution is imperative or reactive.

The two main strategies are:

- `PlatformTransactionManager` for imperative, typically thread-bound transaction workflows;
- `ReactiveTransactionManager` for reactive workflows whose context is carried through Reactor.

Application transaction policy should depend on this abstraction boundary rather than on vendor-specific begin/commit APIs. The concrete manager remains important because it decides which resource is coordinated and which capabilities—such as savepoints, suspension, or isolation—are actually available.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-platform-manager">PlatformTransactionManager and Imperative Transactions</a>

<details>
<summary>Click for details</summary>

`PlatformTransactionManager` is Spring's central imperative transaction interface. Its contract is deliberately small:

```text
getTransaction(TransactionDefinition) → TransactionStatus
commit(TransactionStatus)
rollback(TransactionStatus)
```

`getTransaction` does not always mean “create a new physical transaction.” The manager interprets propagation: it may create one, join an existing one, return a non-transactional status, or reject the call.

`commit` also respects rollback-only state. A call to `commit(status)` is a request to complete according to current status; if the transaction has been marked rollback-only, completion can result in rollback and potentially `UnexpectedRollbackException` for an outer caller.

Concrete managers adapt this contract to JDBC, JPA, JTA, or other imperative resources.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-reactive-manager">ReactiveTransactionManager and Reactive Transactions</a>

<details>
<summary>Click for details</summary>

`ReactiveTransactionManager` expresses the same policy role for non-blocking reactive execution. Its lifecycle operations return reactive publishers, because resource acquisition and completion may themselves be asynchronous.

```text
getReactiveTransaction(definition) → Mono<ReactiveTransaction>
commit(transaction)                → Mono<Void>
rollback(transaction)              → Mono<Void>
```

The important conceptual difference is context propagation. Imperative managers commonly bind resources to a thread; reactive managers use subscriber/Reactor context. The policy vocabulary—propagation, isolation, read-only, timeout—remains familiar, but backend support and context mechanics differ.

Do not mix the two simply because both implement `TransactionManager`. Code must use the execution model that matches the manager and participating resources.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-definition">TransactionDefinition and Transaction Policy</a>

<details>
<summary>Click for details</summary>

`TransactionDefinition` describes **requested transaction characteristics**. It is policy input to a transaction manager, not proof that the backend will honor every option identically.

The common properties are transaction name, propagation behavior, isolation level, timeout, and read-only flag. Declarative `@Transactional` metadata is converted into a `TransactionAttribute`, which extends this policy model with rollback rules and qualifiers/labels used by transaction infrastructure.

```text
policy request
  REQUIRED
  DEFAULT isolation
  timeout = 30s
  readOnly = true
        ↓
TransactionManager interprets against current context + backend capability
```

Some attributes matter only when a new physical transaction is started. A participating scope cannot retroactively change the isolation level of an already-running physical transaction.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-status">TransactionStatus and Transaction Execution State</a>

<details>
<summary>Click for details</summary>

`TransactionStatus` represents the state of an imperative transaction execution as seen by `PlatformTransactionManager`. It lets infrastructure/application code ask whether the execution is new, completed, rollback-only, or associated with a savepoint, and it can explicitly mark rollback-only.

Do not confuse “new logical scope” with `isNewTransaction()`. A `REQUIRED` method may have a new logical boundary while participating in an outer physical transaction; the status tells you about the manager's current execution, not merely annotation nesting.

`TransactionStatus` also extends savepoint/flush capabilities where the manager/resource supports them. Those methods expose generic hooks; support still depends on the concrete transaction system.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-lifecycle">Begin, Commit, Rollback, and Completion</a>

<details>
<summary>Click for details</summary>

At a high level, every transaction workflow follows the same state machine:

```text
resolve transaction definition
→ start or participate according to propagation
→ execute application work
→ choose commit or rollback
→ run completion/synchronization callbacks
→ release or resume resources
```

“Commit” is not simply a method invoked after successful Java code. Before completion, rollback-only state, callback failures, timeout, or transaction-system errors can still change the outcome.

After completion, the transaction is no longer usable for new changes. Resource cleanup/resume belongs to manager/synchronization infrastructure so application code does not manually bind and unbind transaction resources in ordinary use.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-manager-selection">Choosing and Qualifying a Transaction Manager</a>

<details>
<summary>Click for details</summary>

When only one suitable manager exists, Spring transaction infrastructure can use it as the default. In applications with multiple managers, the transaction boundary must identify which one owns the work.

`@Transactional` exposes `transactionManager` (alias `value`) as a qualifier. Framework configuration can also select a default manager through `TransactionManagementConfigurer` when needed.

```java
@Transactional(transactionManager = "ordersTxManager")
public void updateOrder() { ... }
```

Selection is semantic, not cosmetic. Choosing the wrong manager can mean the actual resource operations do not participate in the transaction you thought you opened. Name/qualify managers according to resource ownership and execution model, especially when imperative and reactive managers coexist.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-local-vs-global">Local Resource Transactions vs Coordinated Transactions</a>

<details>
<summary>Click for details</summary>

A local transaction manager usually coordinates one resource family, such as one JDBC `DataSource` or one JPA `EntityManagerFactory`. It is efficient and simple when the business invariant lives inside that resource boundary.

Coordinated/global transactions address a different problem: multiple transactional resources must share one atomic outcome. In Spring's imperative stack, JTA integration can expose that capability when the deployment environment and participating resources support it.

Do not infer global atomicity from the generic `PlatformTransactionManager` interface. The same interface can represent a simple local manager or a global coordinator. Check the concrete manager and resource capabilities, and consider whether distributed coordination cost is justified before choosing it over application-level patterns.

</details>

- [Back to top](#back-to-top)
