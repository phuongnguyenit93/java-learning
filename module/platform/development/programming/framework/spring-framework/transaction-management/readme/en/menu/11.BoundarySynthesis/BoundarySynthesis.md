<a id="back-to-top"></a>

# Transaction Boundary Synthesis for Production Systems

## Menu
- [Service-Layer Transaction Boundary](#transaction-service-layer-boundary)
- [Multiple Managers, Resource Compatibility, and Coordinated Transactions](#transaction-manager-capabilities)
- [Thread and Async Boundaries](#transaction-thread-boundary)
- [Remote Call Boundary](#transaction-remote-boundary)
- [Messaging Boundary and Side-Effect Coordination](#transaction-messaging-boundary)
- [Long-Running Work and Transaction Scope](#transaction-long-running-boundary)
- [Testing and Operational Diagnosis Handoffs](#transaction-testing-handoff)
- [Transaction Boundary Decision Checklist](#transaction-design-checklist)

## <a id="transaction-service-layer-boundary">Service-Layer Transaction Boundary</a>

<details>
<summary>Click for details</summary>

A good transaction boundary usually aligns with one business unit of work, often at the application/service layer. The boundary should be high enough to include all data changes that must succeed together, but not so high that it holds locks/resources while performing unrelated slow work.

```text
request/controller
    ↓
application service  ← transaction boundary often belongs here
    ↓
multiple repositories/data-access operations
```

Putting `@Transactional` on every repository helper fragments policy and can hide the actual business unit of work. Putting it around an entire remote workflow can keep local resources open far too long. Start from the business invariant, then choose the smallest boundary that preserves it.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-manager-capabilities">Multiple Managers, Resource Compatibility, and Coordinated Transactions</a>

<details>
<summary>Click for details</summary>

Applications can contain more than one `TransactionManager`, for example separate local resources or an imperative and reactive manager. In Spring Framework 6.1, `@Transactional(transactionManager = "...")` (alias `value`) supplies the desired manager bean name or qualifier value; the unqualified default manager is configured separately.

Selection does not create capabilities the manager does not have. A local manager for one `DataSource` coordinates that resource; it does not automatically coordinate a second database or message broker. XA/JTA can coordinate **XA-capable transactional resources**—for example suitable database and JMS providers—when the environment and drivers/providers support enlistment. An HTTP/RPC service is a different remote/distributed boundary and is not made XA-transactional merely by choosing a JTA manager; it requires application-level coordination such as idempotency, retries, compensation, saga, or durable messaging patterns.

Always match the boundary to manager capability: resource type, suspension, savepoints, isolation, reactive vs imperative execution, and multi-resource coordination are all part of the decision.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-thread-boundary">Thread and Async Boundaries</a>

<details>
<summary>Click for details</summary>

Imperative Spring transactions are thread-bound. Starting work on another thread does not automatically carry the current transaction's resources or synchronization state. This is why combining `@Transactional` with executors or `@Async` requires explicit reasoning about where the transaction actually starts.

```text
thread A: transactional method ──> submit task
                                  ↓
thread B: task runs with its own context
```

If the task needs a transaction, give the task's execution path its own transaction boundary rather than assuming thread A's transaction follows it. Reactive transactions use Reactor Context instead, but arbitrary escape into non-reactive asynchronous mechanisms can still leave that context.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-remote-boundary">Remote Call Boundary</a>

<details>
<summary>Click for details</summary>

A local Spring transaction does not propagate across an HTTP/RPC boundary just because the caller is transactional. The remote service has its own process, resources, and transaction manager.

Holding a database transaction open while making a slow remote call increases lock duration and couples local rollback to network latency without creating distributed atomicity. If the remote call succeeds and the local transaction later rolls back, the remote side is not automatically undone.

Design remote workflows with explicit distributed-systems patterns—idempotency, retries, compensation, sagas, or durable messages—as appropriate. This module owns the boundary recognition; the detailed distributed pattern belongs to the relevant architecture/integration modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-messaging-boundary">Messaging Boundary and Side-Effect Coordination</a>

<details>
<summary>Click for details</summary>

Publishing a message and updating a database are two side effects unless a transaction technology explicitly coordinates both resources. A local database transaction alone cannot guarantee that "row committed" and "message published" happen atomically.

Common application designs therefore coordinate the boundary explicitly. Examples include publishing a transaction-bound event after commit, writing an outbox record in the same database transaction and publishing it later, or using a transaction manager that genuinely coordinates the participating resources.

Each choice has a different failure model. An `AFTER_COMMIT` listener avoids publishing for rolled-back database work, but a crash after commit and before external publication can still lose the side effect unless it is made durable. Do not confuse callback timing with distributed atomicity.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-long-running-boundary">Long-Running Work and Transaction Scope</a>

<details>
<summary>Click for details</summary>

Transactions hold scarce resources: database connections, locks, snapshots, and transaction-manager state. A boundary that spans user think time, large CPU work, file transfer, remote APIs, or long waits increases contention and failure exposure.

A useful design technique is to separate phases:

```text
slow preparation outside transaction
→ short transactional state change
→ post-commit side effect / asynchronous continuation
```

Do not split blindly—atomic business invariants still matter. The goal is to minimize the time a transaction is open while keeping all state changes that must be atomic inside the same unit of work.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-testing-handoff">Testing and Operational Diagnosis Handoffs</a>

<details>
<summary>Click for details</summary>

Transaction design should be testable and observable, but this module does not own the Spring TestContext transaction lifecycle or full observability stack.

For testing, verify business outcomes under commit and rollback conditions, propagation interactions, and manager selection. Test-managed transactions, `TransactionalTestExecutionListener`, and test rollback semantics belong to the Spring Testing module because their lifecycle differs from production service transactions.

For diagnosis, useful evidence includes transaction-manager debug logs, transaction names, active/read-only state, rollback-only flags, and lifecycle observation through `TransactionExecutionListener` where appropriate. Metrics/tracing integration should observe the transaction without changing its business semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-design-checklist">Transaction Boundary Decision Checklist</a>

<details>
<summary>Click for details</summary>

Before finalizing a transaction boundary, ask a small set of questions:

1. **What business invariant must be atomic?** That defines the real unit of work.
2. **Which resources participate?** One database, multiple databases, a broker, or remote services have different coordination needs.
3. **Which transaction manager owns those resources?** Verify imperative/reactive model and backend capabilities.
4. **What propagation is required?** Prefer `REQUIRED` unless independent or nested semantics are intentional.
5. **What causes rollback?** Make checked-exception and rollback-only behavior explicit.
6. **How long is the resource held?** Move unrelated slow work outside when possible.
7. **Where do guarantees stop?** Threads, remote calls, messages, and post-commit work need explicit boundaries.
8. **How will failures be observed and tested?** A transaction policy that cannot be diagnosed is hard to operate.

The purpose of Spring transaction management is not to hide these decisions. It gives one consistent abstraction for expressing them deliberately.

</details>

- [Back to top](#back-to-top)
