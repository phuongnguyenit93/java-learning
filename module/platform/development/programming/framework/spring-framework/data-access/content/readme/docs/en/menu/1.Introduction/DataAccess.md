<a id="back-to-top"></a>

# Spring Framework Data Access

## Menu
- [What Spring Data Access Is and Why It Exists](#data-access-purpose)
- [What Becomes Repetitive Without Spring Data Access](#data-access-problem-without-spring)
- [Spring Framework Data Access Responsibility and Boundaries](#data-access-responsibility-boundary)
- [Blocking JDBC and Reactive R2DBC Paths](#jdbc-vs-r2dbc-paths)

## <a id="data-access-purpose">What Spring Data Access Is and Why It Exists</a>

<details>
<summary>Click for details</summary>

Spring Data Access is Spring Framework's infrastructure layer for working with persistence technologies without forcing application code to repeat low-level resource management and technology-specific error handling. In this module, the two main concrete paths are Spring JDBC and Spring R2DBC.

The key mental model is **application intent above, resource workflow below**. Application code should express SQL, parameters, and how rows become useful values. Spring takes responsibility for the repetitive workflow around obtaining resources, invoking driver APIs, translating failures, and releasing resources correctly.

This layer is deliberately lower than Spring Data repositories or ORM mapping. It does not decide aggregate boundaries, entity state transitions, or derived queries. It gives application code a disciplined way to use relational technologies directly when explicit SQL and direct control are valuable.

~~~text
business operation
    ↓
DAO / data-access component
    ↓
JdbcClient / JdbcTemplate / DatabaseClient
    ↓
JDBC DataSource or R2DBC ConnectionFactory
    ↓
database driver and database
~~~

The abstraction is valuable because the repetitive mechanics become consistent while SQL and database behavior remain visible.

</details>

- [Back to top](#back-to-top)

---

## <a id="data-access-problem-without-spring">What Becomes Repetitive Without Spring Data Access</a>

<details>
<summary>Click for details</summary>

Raw database APIs are usable, but every call carries operational ceremony. With JDBC, code must obtain a Connection, create and parameterize a statement, execute it, traverse results, translate failures, and close resources in the correct order. R2DBC removes blocking I/O from the API model but still requires careful connection, statement, result, and error handling.

Without a framework-level workflow, this ceremony tends to spread across DAOs. That creates recurring problems:

- cleanup code obscures the SQL and mapping logic;
- one forgotten close can leak a scarce connection;
- each DAO may classify vendor exceptions differently;
- transaction-bound resources are easy to bypass accidentally;
- tests become coupled to plumbing instead of the data-access decision being exercised.

Spring does **not** eliminate the database's own constraints. SQL can still be slow, a unique key can still be violated, a lock can still time out, and network failures can still happen. Spring changes the consistency of the Java-side contract around those events.

The goal is therefore not "less SQL". It is **less accidental resource and exception plumbing around intentional SQL**.

</details>

- [Back to top](#back-to-top)

---

## <a id="data-access-responsibility-boundary">Spring Framework Data Access Responsibility and Boundaries</a>

<details>
<summary>Click for details</summary>

This module owns the Spring Framework mechanisms that sit directly on JDBC and R2DBC: the org.springframework.dao exception model, Spring JDBC templates and JdbcClient, Spring R2DBC DatabaseClient, connection/resource helpers, and the boundary where those resources participate in Spring-managed transactions.

Several adjacent concerns have different owners:

- **Plain JDBC** owns JDBC API semantics such as Connection, PreparedStatement, transaction methods, and driver behavior.
- **Spring Transaction Management** owns transaction demarcation, propagation, isolation, rollback rules, and transaction-manager policy.
- **Spring Data JDBC/R2DBC** owns repositories, aggregate mapping, query derivation, and higher-level persistence models.
- **JPA/Hibernate** own entity mapping, persistence-context semantics, dirty checking, and ORM lifecycle.
- **Reactive Programming** owns Reactive Streams, backpressure, and Reactor composition fundamentals.

Those boundaries matter because a data-access API should not be mistaken for a complete persistence architecture. JdbcClient can map rows to objects, but that is not the same responsibility as aggregate mapping. DatabaseClient returns reactive publishers, but that does not make this module the owner of Reactor semantics.

When a concept appears only for comparison or handoff, this module explains just enough to make the boundary clear and sends the deeper learning journey to its owner.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-vs-r2dbc-paths">Blocking JDBC and Reactive R2DBC Paths</a>

<details>
<summary>Click for details</summary>

Spring exposes two relational paths with different execution models.

**Spring JDBC** sits on the blocking JDBC specification. A database call occupies the calling thread while the driver waits for I/O. The central resource is a JDBC Connection obtained from a DataSource, and imperative transaction participation is commonly associated with resources bound to the executing thread.

**Spring R2DBC** sits on the non-blocking R2DBC SPI. A ConnectionFactory supplies reactive connections and DatabaseClient exposes operations as Reactive Streams publishers. Work occurs on subscription, and reactive transaction/resource context is propagated through Reactor context rather than ordinary thread affinity.

The two stacks solve similar application-level problems—parameterized SQL, result mapping, resource cleanup, and common DataAccessException semantics—but they are not interchangeable wrappers.

~~~text
JDBC                            R2DBC
DataSource                      ConnectionFactory
Connection                      io.r2dbc.spi.Connection
JdbcClient / JdbcTemplate       DatabaseClient
blocking call chain             Publisher-based execution
thread-oriented context         subscriber/Reactor context
~~~

Choose from the application's end-to-end execution model. Wrapping a blocking JDBC call in a reactive type does not turn the driver into non-blocking I/O.

</details>

- [Back to top](#back-to-top)
