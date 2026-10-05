<a id="back-to-top"></a>

# Transaction-Bound Events and Lifecycle Hooks

## Menu
- [TransactionalEventListener and Transaction Phases](#transaction-event-listener)
- [Fallback Execution Outside a Transaction](#transaction-event-fallback)
- [Reactive Transaction Events in Spring 6.1](#transaction-reactive-events)
- [Resource Access After Transaction Completion](#transaction-post-completion-resources)
- [TransactionExecutionListener vs Transaction Synchronization](#transaction-execution-listener)

## <a id="transaction-event-listener">TransactionalEventListener and Transaction Phases</a>

<details>
<summary>Click for details</summary>

`@TransactionalEventListener` binds an application-event listener to a transaction phase. Its default phase is `AFTER_COMMIT`, which is useful when a side effect should happen only after the publishing transaction commits successfully.

Available phases are `BEFORE_COMMIT`, `AFTER_COMMIT`, `AFTER_ROLLBACK`, and `AFTER_COMPLETION`. `AFTER_COMPLETION` runs after either commit or rollback and therefore should not be used when the listener needs to know that commit specifically succeeded unless the outcome is checked separately.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderCreated(OrderCreated event) {
    // side effect that is meaningful only after commit
}
```

This mechanism coordinates callback timing with a transaction; it does not turn arbitrary external side effects into part of the same atomic transaction.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-event-fallback">Fallback Execution Outside a Transaction</a>

<details>
<summary>Click for details</summary>

By default, a `@TransactionalEventListener` is **not invoked** when the event is published outside an active transaction, because there is no transaction phase to bind to. Setting `fallbackExecution = true` allows the listener to run anyway.

Fallback changes the semantic contract: the same listener can now be invoked in two different situations—one coordinated with a transaction phase and one with no transaction at all. That may be appropriate for idempotent notification-style behavior, but dangerous when the listener assumes committed database state.

Use fallback only when the no-transaction behavior is deliberately defined and tested. Do not enable it merely to make a listener "always run".

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-reactive-events">Reactive Transaction Events in Spring 6.1</a>

<details>
<summary>Click for details</summary>

Since Spring Framework 6.1, transactional event listeners support both thread-bound transactions managed by `PlatformTransactionManager` and reactive transactions managed by `ReactiveTransactionManager`.

Reactive transactions cannot recover their transaction state from a thread-local variable. Therefore the transaction context must be carried in the **event source**. `TransactionalEventPublisher` is the helper designed for this purpose: it publishes an event whose source is the current Reactor-managed `TransactionContext`.

The important distinction is not the annotation itself but how transaction context reaches the listener. For imperative transactions, the current thread-bound context is visible. For reactive transactions, publishing must preserve the Reactor transaction context explicitly.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-post-completion-resources">Resource Access After Transaction Completion</a>

<details>
<summary>Click for details</summary>

`AFTER_COMMIT`, `AFTER_ROLLBACK`, and `AFTER_COMPLETION` run **after** the transaction outcome has been decided. Spring warns that the transactional resources may still be active and accessible at that moment even though the transaction has already completed.

That creates a subtle trap: data-access code invoked from such a listener may appear to use the original resource and "participate", but additional changes will not be committed to the already-completed transaction.

If post-completion work needs its own durable database changes, run that work in a genuinely new transaction, for example through a separate proxied service method using `PROPAGATION_REQUIRES_NEW` when the transaction manager supports it. A default `REQUIRED` boundary can still see/participate in the bound completed resource and does not make new writes part of a new committable transaction. Reason separately about failure/retry, and do not assume that access to an open resource means the original transaction is still committable.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-execution-listener">TransactionExecutionListener vs Transaction Synchronization</a>

<details>
<summary>Click for details</summary>

`TransactionExecutionListener`, introduced in Spring Framework 6.1, is a stateless lifecycle-observation callback that can be registered with configurable transaction managers. It observes transaction creation/completion steps and is useful for statistics, tracing, diagnostics, or policy observation at the manager level.

That role differs from `TransactionSynchronization`. Synchronization belongs to an individual transaction context and coordinates resource/application callbacks such as `beforeCommit`, `afterCommit`, and `afterCompletion`. An execution listener observes the transaction manager's execution lifecycle rather than acting as a resource-bound participant.

Use the distinction to keep responsibilities clear:

```text
TransactionExecutionListener
→ observe manager-level transaction lifecycle

TransactionSynchronization
→ coordinate callbacks/resources inside a transaction context
```

</details>

- [Back to top](#back-to-top)
