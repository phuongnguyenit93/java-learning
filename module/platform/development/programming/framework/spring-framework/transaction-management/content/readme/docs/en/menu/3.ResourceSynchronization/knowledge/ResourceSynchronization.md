<a id="back-to-top"></a>

# Resource Synchronization and Data-Access Participation

## Menu
- [Transaction-Bound Resources and Resource Reuse](#transaction-bound-resources)
- [Transaction Synchronization Lifecycle and Cleanup](#transaction-synchronization)
- [Imperative TransactionSynchronizationManager Mental Model](#transaction-synchronization-manager)
- [Transaction-Aware Data-Access Infrastructure](#transaction-aware-infrastructure)
- [Transaction Coordination vs Concrete Data-Access Mechanics](#transaction-resource-boundary)

## <a id="transaction-bound-resources">Transaction-Bound Resources and Resource Reuse</a>

<details>
<summary>Click for details</summary>

When several data-access operations for the same resource factory participate in one imperative transaction, Spring-aware access normally reuses the resource associated with that transaction instead of opening an unrelated resource behind the transaction manager. Spring models this with **transaction-bound resources**, keyed by resources such as a `DataSource` or session factory.

For example, a JDBC transaction manager can bind a connection holder for a `DataSource` to the current transaction context. Spring-aware data-access code looks up that bound resource and reuses it for the unit of work.

```text
transaction starts
  ↓
resource bound to context
  ↓
repository call A ─┐
repository call B ─┼→ reuse transactional resource
repository call C ─┘
  ↓
completion → resource unbound/released
```

The exact resource object differs across JDBC, JPA, Hibernate, and other integrations. A coordinated transaction may enlist multiple different resources; each has its own integration/binding model. The transaction-management lesson is the reuse/binding model for participating resources, while the resource APIs themselves belong to data-access modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-synchronization">Transaction Synchronization Lifecycle and Cleanup</a>

<details>
<summary>Click for details</summary>

Resource binding alone is not enough. Components also need lifecycle callbacks so they can flush, release, suspend, resume, or run work around transaction completion. Spring represents these callbacks through `TransactionSynchronization`.

Typical imperative callbacks include `beforeCommit`, `beforeCompletion`, `afterCommit`, and `afterCompletion`; synchronization objects can also participate in suspend/resume and flush behavior. The transaction manager activates synchronization, invokes callbacks in the appropriate phase, and clears synchronization when completion finishes.

Do not use synchronization callbacks as a substitute for the transaction manager itself. They observe or coordinate work around a transaction that already exists; they do not independently provide atomicity.

Cleanup must be completion-safe. Code should not leave a resource bound after commit/rollback, because a later request on the same thread could accidentally see stale transaction state.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-synchronization-manager">Imperative TransactionSynchronizationManager Mental Model</a>

<details>
<summary>Click for details</summary>

The imperative `org.springframework.transaction.support.TransactionSynchronizationManager` is the central delegate that manages resources and synchronizations **per thread**. It is infrastructure-level API: typical business code should not manually bind resources.

It can answer questions such as:

- whether synchronization is active;
- whether an actual transaction is active;
- the current transaction name/read-only/isolation metadata;
- whether a resource is bound for a particular key.

Resource-management code may call `getResource(key)` to reuse a bound connection/session. Transaction managers are responsible for activating synchronization and binding/unbinding their resources.

This class is specifically the imperative mental model. Reactive transaction management has a different `TransactionSynchronizationManager` in the reactive package that works with subscriber context rather than thread-local state.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-aware-infrastructure">Transaction-Aware Data-Access Infrastructure</a>

<details>
<summary>Click for details</summary>

Spring's data-access infrastructure participates in transactions by consulting the transaction context instead of bypassing it. Helpers such as `DataSourceUtils` can obtain a connection that is aware of a Spring-managed JDBC transaction; similar integration exists for other supported resource technologies.

This is why ordinary repository/data-access code can often remain free of explicit transaction APIs:

```text
service transaction boundary
        ↓
transaction manager binds resource
        ↓
Spring-aware data-access helper discovers bound resource
        ↓
repository work participates automatically
```

The important requirement is that the data-access path must use infrastructure capable of participating in that Spring transaction. Opening raw independent resources behind Spring's back can bypass the expected transaction.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-resource-boundary">Transaction Coordination vs Concrete Data-Access Mechanics</a>

<details>
<summary>Click for details</summary>

Transaction management owns **coordination policy**: when a resource should participate, when completion happens, and which callbacks run. It does not own every concrete data-access behavior.

For example:

```text
this module
→ resource binding, synchronization, participation, lifecycle

data-access module
→ JdbcTemplate/JdbcClient/DatabaseClient APIs, SQL execution, mapping

persistence/ORM module
→ entity state, flush modes, mapping, lazy loading
```

Crossing the boundary is sometimes necessary to explain an effect, such as “a JPA flush can occur before commit,” but the detailed API belongs elsewhere. Keeping the boundary explicit prevents transaction learning from turning into a JDBC/JPA/R2DBC tutorial.

</details>

- [Back to top](#back-to-top)
