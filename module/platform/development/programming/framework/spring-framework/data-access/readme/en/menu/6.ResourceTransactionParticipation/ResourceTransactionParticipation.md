<a id="back-to-top"></a>

# Resource Lifecycle and Transaction Participation

## Menu
- [Resource Acquisition and Release](#resource-acquisition-release)
- [DataSourceUtils and JDBC Transaction Participation](#datasource-utils-transaction-participation)
- [ConnectionFactoryUtils and R2DBC Transaction Participation](#connection-factory-utils-transaction-participation)
- [Transaction-Aware DataSource and ConnectionFactory Proxies](#transaction-aware-resource-proxies)
- [Thread-Bound and Subscriber-Context-Bound Resources](#thread-vs-subscriber-context-resources)
- [Boundary with Spring Transaction Management](#transaction-management-boundary)

## <a id="resource-acquisition-release">Resource Acquisition and Release</a>

<details>
<summary>Click for details</summary>


Connections are scarce resources. Correct data access must pair acquisition with release while still allowing a transaction to reuse one logical resource across multiple operations.

Spring's templates/clients hide this ceremony, but the underlying model is important:

- outside a managed transaction, an operation obtains a connection and releases it when the operation completes;
- inside a synchronized transaction, Spring can reuse the resource associated with that transaction;
- application code should not independently close a resource that Spring owns for the transaction.

The resource helper classes exist for lower-level code that must participate in the same lifecycle. DataSourceUtils covers JDBC; ConnectionFactoryUtils covers R2DBC.

The rule is simple: **use the highest Spring abstraction available**. Reach for resource utilities only when integrating lower-level code. Otherwise let JdbcTemplate/JdbcClient/DatabaseClient perform the correct acquisition and release behavior behind the scenes.
</details>

- [Back to top](#back-to-top)

---

## <a id="datasource-utils-transaction-participation">DataSourceUtils and JDBC Transaction Participation</a>

<details>
<summary>Click for details</summary>


DataSourceUtils provides static JDBC Connection access that is aware of Spring transaction synchronization. getConnection(dataSource) returns a Connection associated with the current transaction when one exists; otherwise it obtains a new Connection and can bind it when synchronization is active.

That means custom JDBC code can participate in the same transaction as JdbcTemplate without opening an unrelated Connection.

~~~java
Connection con = DataSourceUtils.getConnection(dataSource);
try {
    // custom JDBC operation
}
finally {
    DataSourceUtils.releaseConnection(con, dataSource);
}
~~~

Use releaseConnection rather than calling close() unconditionally. The utility knows whether the Connection is transaction-bound and therefore whether physical close is appropriate.

Most application code should not need this pattern because JdbcTemplate and Spring JDBC operation objects use DataSourceUtils internally. Direct use belongs at an integration/lower-level boundary, not in every DAO.
</details>

- [Back to top](#back-to-top)

---

## <a id="connection-factory-utils-transaction-participation">ConnectionFactoryUtils and R2DBC Transaction Participation</a>

<details>
<summary>Click for details</summary>


ConnectionFactoryUtils plays the corresponding role for R2DBC. It obtains Connections from a ConnectionFactory, translates acquisition failures into DataAccessException, and understands connections associated with reactive transaction synchronization.

The important difference is context. Reactive transaction resources are not safely modeled as ordinary ThreadLocal state because a reactive sequence can continue on different threads. Spring's reactive transaction infrastructure associates state with the subscriber/Reactor context.

~~~java
Mono<Connection> connection =
    ConnectionFactoryUtils.getConnection(connectionFactory);
~~~

DatabaseClient uses the helper internally, so direct calls are usually unnecessary. They are appropriate when custom R2DBC code needs a raw Connection but must still participate in the Spring-managed reactive resource lifecycle.

Always compose the resulting Publisher. Acquiring a connection reactively and then escaping it into imperative shared state breaks the lifecycle assumptions that make cleanup and transaction participation reliable.
</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-aware-resource-proxies">Transaction-Aware DataSource and ConnectionFactory Proxies</a>

<details>
<summary>Click for details</summary>


TransactionAwareDataSourceProxy and TransactionAwareConnectionFactoryProxy adapt **unaware client code** to Spring-managed transaction resources.

TransactionAwareDataSourceProxy implements DataSource and delegates getConnection() through Spring's transaction-aware resource lookup. It is mainly useful when legacy or third-party code insists on receiving a standard DataSource and calls Connection.close() itself.

The R2DBC proxy serves the analogous role for ConnectionFactory-based code.

These proxies are not the preferred API for new Spring data-access code. JdbcTemplate, JdbcClient, DatabaseClient, DataSourceUtils, and ConnectionFactoryUtils already know how to participate in Spring-managed resources.

~~~text
new Spring-aware code
    → use Spring client/helper directly

existing code requiring standard DataSource/ConnectionFactory
    → transaction-aware proxy may adapt it
~~~

Treat proxies as integration adapters. Adding one everywhere can obscure resource ownership and make configuration harder to reason about.
</details>

- [Back to top](#back-to-top)

---

## <a id="thread-vs-subscriber-context-resources">Thread-Bound and Subscriber-Context-Bound Resources</a>

<details>
<summary>Click for details</summary>


Imperative JDBC transaction participation is commonly thread-bound: Spring's imperative transaction synchronization associates a ConnectionHolder with the current thread for the duration of the transaction.

Reactive R2DBC cannot rely on that model. Reactive execution may move between threads while remaining one logical subscription. Spring's reactive transaction synchronization therefore uses Reactor subscriber Context to carry transaction state and associated resources.

~~~text
imperative JDBC
request thread
    → transaction context
    → JDBC Connection

reactive R2DBC
subscriber chain
    → Reactor Context
    → R2DBC Connection
~~~

This distinction explains why ThreadLocal-based assumptions fail for reactive transaction state. A thread switch does not define transaction participation: Spring associates reactive transaction state with the Reactor Context carried by the subscription, not with whichever thread happens to process a signal. Manually storing an R2DBC Connection in a ThreadLocal therefore bypasses that model.

The transaction module owns the full semantics. Here the learner only needs enough context to understand why resource lookup differs between JDBC and R2DBC.
</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-management-boundary">Boundary with Spring Transaction Management</a>

<details>
<summary>Click for details</summary>


Data Access owns **how a JDBC or R2DBC resource joins Spring's resource lifecycle**. Transaction Management owns **when transactions begin/end and what policy they use**.

This chapter therefore stops before teaching:

- @Transactional interception and proxy semantics;
- propagation modes such as REQUIRED or REQUIRES_NEW;
- isolation-level choices;
- rollback rules and rollback-only behavior;
- programmatic TransactionTemplate or TransactionalOperator design;
- transaction-bound event semantics.

Data Access needs to know that a resource can be bound to a transaction so templates/clients reuse the right connection. It does not need to redefine transaction policy.

A practical handoff is:

~~~text
Data Access
    → "this operation can use the transaction-associated resource"

Transaction Management
    → "this call should run in transaction X with policy Y"
~~~

Keeping the boundary explicit prevents learners from confusing a connection helper with a transaction manager.
</details>

- [Back to top](#back-to-top)
