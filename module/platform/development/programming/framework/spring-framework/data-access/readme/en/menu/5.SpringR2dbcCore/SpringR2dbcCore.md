<a id="back-to-top"></a>

# Spring R2DBC Core and DatabaseClient

## Menu
- [ConnectionFactory and DatabaseClient](#r2dbc-connection-factory-database-client)
- [Reactive SQL Execution Model](#r2dbc-reactive-sql-execution)
- [Named Parameters and Database Bind Markers](#r2dbc-parameter-binding-bind-markers)
- [Result Mapping, Cardinality, and Null Constraints](#r2dbc-result-mapping-cardinality-null)
- [Generated Values and Statement Filters](#r2dbc-generated-values-statement-filters)

## <a id="r2dbc-connection-factory-database-client">ConnectionFactory and DatabaseClient</a>

<details>
<summary>Click for details</summary>


R2DBC defines a non-blocking SPI for relational database access. Its resource entry point is ConnectionFactory, analogous in responsibility—but not in execution model—to JDBC DataSource.

Spring Framework's org.springframework.r2dbc.core.DatabaseClient is the central client in the framework's R2DBC core package. It creates/releases resources, executes statements, maps results, and translates R2DBC failures into DataAccessException. This ownership belongs to Spring Framework, not Spring Data R2DBC.

~~~java
DatabaseClient client = DatabaseClient.create(connectionFactory);

Mono<Customer> customer = client.sql(
        "select id, name from customer where id = :id")
    .bind("id", id)
    .map((row, metadata) -> new Customer(
        row.get("id", Long.class),
        row.get("name", String.class)))
    .one();
~~~

Spring Data R2DBC builds higher-level mapping/repository abstractions above this foundation. Use DatabaseClient when explicit SQL and framework-level reactive access are the goal; move upward only when repository/entity mapping is actually useful.
</details>

- [Back to top](#back-to-top)

---

## <a id="r2dbc-reactive-sql-execution">Reactive SQL Execution Model</a>

<details>
<summary>Click for details</summary>


A DatabaseClient call builds a reactive pipeline; it does not imply that database I/O happens at the point where the fluent chain is assembled. Execution occurs when the returned Publisher is subscribed.

This matters for control flow and resource lifecycle:

~~~text
build SQL specification
    ↓
return Mono/Flux
    ↓
subscription
    ↓
obtain Connection
    ↓
create/bind/execute Statement
    ↓
consume Result publishers
    ↓
release resource
~~~

Reactive composition therefore belongs in the same lifecycle as the database work. An error can be transformed with Reactor operators, cancellation can stop downstream demand, and transaction context must travel with the reactive chain.

DatabaseClient is thread-safe once configured and can be shared. The client retains configuration such as the ConnectionFactory; per-operation state belongs to the fluent execution specification.

Do not call block() merely to make DatabaseClient look imperative. Blocking at the edge may be deliberate in a non-reactive application, but doing so inside a reactive request path defeats the non-blocking model and can cause thread starvation.
</details>

- [Back to top](#back-to-top)

---

## <a id="r2dbc-parameter-binding-bind-markers">Named Parameters and Database Bind Markers</a>

<details>
<summary>Click for details</summary>


R2DBC drivers use database-specific bind-marker syntax. PostgreSQL may use numbered markers such as $1, while other drivers may expose ? or another native form. DatabaseClient lets application SQL use Spring-style named parameters and translates them into bind markers appropriate for the ConnectionFactory.

Named-parameter expansion is enabled by default. DatabaseClient.Builder can supply a BindMarkersFactory explicitly or disable named parameters.

~~~java
client.sql("""
        select * from customer
        where status = :status and region in (:regions)
        """)
    .bind("status", "ACTIVE")
    // collection expansion is handled by Spring's named-parameter processing
    .bind("regions", List.of("EU", "APAC"));
~~~

The essential distinction is:

~~~text
application SQL names parameters
        ↓
Spring expands / resolves names
        ↓
driver receives native bind markers
~~~

Binding protects values; it does not make dynamic SQL identifiers safe. Table/column names and arbitrary SQL fragments must still be chosen from trusted application-controlled values.
</details>

- [Back to top](#back-to-top)

---

## <a id="r2dbc-result-mapping-cardinality-null">Result Mapping, Cardinality, and Null Constraints</a>

<details>
<summary>Click for details</summary>


R2DBC rows are mapped reactively, but result cardinality still needs an explicit contract. DatabaseClient's RowsFetchSpec supports one(), first(), and all() style result resolution.

Use one() only when zero-or-one semantics are valid and duplicate rows should be treated as an error. Use all() for a stream of rows. first() intentionally accepts the first matching row, so do not use it to hide a query that should be unique.

Reactive Streams does not permit null elements. A mapping function must return a non-null value for an emitted item. SQL NULL should be represented inside the mapped value—for example as a nullable field, Optional-like domain representation, or another explicit model—not as a null Publisher element.

~~~java
Flux<Customer> customers = client.sql(sql)
    .map((row, metadata) -> new Customer(
        row.get("id", Long.class),
        row.get("name", String.class)))
    .all();
~~~

Keep SQL type conversion explicit when driver behavior is ambiguous. A row mapper is a boundary between driver-level values and application-level values; it should not silently invent persistence semantics that belong to a higher mapping layer.
</details>

- [Back to top](#back-to-top)

---

## <a id="r2dbc-generated-values-statement-filters">Generated Values and Statement Filters</a>

<details>
<summary>Click for details</summary>


Some database operations need statement options beyond simple SQL and bindings. DatabaseClient exposes StatementFilterFunction so application code can customize the R2DBC Statement or wrap execution without abandoning the client's resource/error workflow.

A common example is requesting generated values:

~~~java
Mono<Long> id = client.sql(
        "insert into customer(name) values (:name)")
    .bind("name", "Ada")
    .filter(statement -> statement.returnGeneratedValues("id"))
    .map((row, metadata) -> row.get("id", Long.class))
    .one();
~~~

Statement filters can also configure options such as fetch size when the driver supports them. They are deliberately lower-level than ordinary fluent operations; use them only for behavior the normal DatabaseClient API does not express.

Generated-value behavior remains database/driver specific. Column names, supported SQL forms, and returned types can differ. Keep that assumption local to the DAO and test it against the actual R2DBC driver rather than assuming JDBC-generated-key behavior transfers unchanged.
</details>

- [Back to top](#back-to-top)
