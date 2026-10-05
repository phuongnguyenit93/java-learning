<a id="back-to-top"></a>

# Choosing the Data Access Abstraction

## Menu
- [Choosing the Spring JDBC Client Layer](#choose-jdbc-client-layer)
- [Choosing JDBC or R2DBC](#choose-jdbc-or-r2dbc)
- [Choosing Spring Data, ORM, or Framework-Level Access](#choose-persistence-abstraction-level)
- [Avoiding Blocking JDBC in Reactive Paths](#blocking-jdbc-in-reactive-paths)
- [End-to-End Spring Data Access Mental Model](#data-access-end-to-end-model)

## <a id="choose-jdbc-client-layer">Choosing the Spring JDBC Client Layer</a>

<details>
<summary>Click for details</summary>


Within the blocking Spring JDBC stack, choose the narrowest API that makes the intent obvious.

Use JdbcClient for common parameterized queries and updates. It gives a concise fluent surface and unifies positional and named-parameter use.

Use JdbcTemplate or NamedParameterJdbcTemplate when lower-level callbacks, explicit template configuration, batch APIs, or mature template-specific features are required.

Use SimpleJdbcInsert or SimpleJdbcCall when their metadata-assisted insert/procedure model matches the operation.

~~~text
ordinary query/update          → JdbcClient
advanced JDBC workflow         → template APIs
metadata-assisted insert/call  → SimpleJdbcInsert / SimpleJdbcCall
~~~

The APIs are layers in one Spring JDBC ecosystem, not mutually exclusive generations. A codebase can choose a preferred default while still using lower-level infrastructure where it makes the operation clearer.
</details>

- [Back to top](#back-to-top)

---

## <a id="choose-jdbc-or-r2dbc">Choosing JDBC or R2DBC</a>

<details>
<summary>Click for details</summary>


Choose JDBC or R2DBC from the application's **end-to-end execution model**, driver support, and operational needs.

JDBC is appropriate when the application is imperative/blocking, the ecosystem relies on JDBC-only libraries, or database drivers/tooling are strongest there. Mature connection pools and broad vendor support make it the default for many applications.

R2DBC is appropriate when a genuinely reactive stack needs non-blocking database access and a production-quality R2DBC driver exists for the target database. It keeps database calls inside the Publisher-based flow instead of forcing a blocking bridge.

R2DBC is not "faster JDBC". Non-blocking I/O can improve resource utilization under concurrency, but database capacity, SQL quality, locks, network latency, and pool configuration still dominate many workloads.

Do not choose a model only because the surrounding framework supports it. Choose one that is coherent from request handling through transaction/data access to the driver.
</details>

- [Back to top](#back-to-top)

---

## <a id="choose-persistence-abstraction-level">Choosing Spring Data, ORM, or Framework-Level Access</a>

<details>
<summary>Click for details</summary>


Spring Framework data access is the right level when explicit SQL and direct relational control are important. Higher abstractions solve different problems.

- Spring Data JDBC adds aggregate mapping, repositories, and query abstractions on a blocking relational foundation.
- Spring Data R2DBC adds mapping/repository abstractions for R2DBC.
- JPA/Hibernate add ORM concepts such as entity identity, persistence context, lazy associations, dirty checking, and object-relational lifecycle.

A higher abstraction is not automatically better. It trades direct SQL visibility for mapping/repository conventions and more persistence behavior.

~~~text
need explicit SQL + thin framework workflow
    → Spring JDBC / Spring R2DBC

need aggregate/repository mapping
    → Spring Data JDBC / R2DBC

need ORM entity lifecycle and relationship model
    → JPA / Hibernate
~~~

Choose based on the dominant problem. Avoid introducing a repository or ORM layer merely to hide a small amount of already-clear SQL.
</details>

- [Back to top](#back-to-top)

---

## <a id="blocking-jdbc-in-reactive-paths">Avoiding Blocking JDBC in Reactive Paths</a>

<details>
<summary>Click for details</summary>


A reactive method signature does not make blocking JDBC non-blocking. If a WebFlux/Reactor path calls JdbcClient or JdbcTemplate on an event-loop thread, the thread can remain blocked for the duration of database I/O.

When JDBC must coexist with a reactive application, isolate blocking work on an appropriate bounded scheduler and account for connection-pool limits. That can be a valid compatibility strategy, but it is still blocking work with a thread cost.

~~~java
Mono<Customer> customer = Mono.fromCallable(
        () -> jdbcClient.sql(sql)
            .param("id", id)
            .query(Customer.class)
            .single())
    .subscribeOn(Schedulers.boundedElastic());
~~~

This pattern is a boundary adapter, not a substitute for R2DBC. It can protect event-loop threads but does not create non-blocking database I/O.

If the application needs a fully reactive data path and the database has a suitable driver, use R2DBC. If the application is mostly imperative, forcing R2DBC may add complexity without a corresponding benefit.
</details>

- [Back to top](#back-to-top)

---

## <a id="data-access-end-to-end-model">End-to-End Spring Data Access Mental Model</a>

<details>
<summary>Click for details</summary>


The module can be summarized as a sequence of explicit decisions rather than a list of classes.

~~~text
1. Choose execution model
   imperative JDBC | reactive R2DBC

2. Provide resource factory
   DataSource | ConnectionFactory

3. Choose Spring access layer
   JdbcClient/Template | DatabaseClient

4. Bind values and execute explicit SQL

5. Map rows with a cardinality contract

6. Let Spring translate native failures
   → DataAccessException

7. Let Spring resource helpers participate
   in a managed transaction when one exists

8. Move upward only when needed
   → Spring Data / ORM / transaction policy
~~~

The central trade-off is control versus abstraction. Spring Framework Data Access removes accidental plumbing while leaving the relational operation visible.

A mature DAO should make resource ownership, SQL intent, result expectations, and failure meaning easier to reason about—not hide them behind unnecessary layers.
</details>

- [Back to top](#back-to-top)
