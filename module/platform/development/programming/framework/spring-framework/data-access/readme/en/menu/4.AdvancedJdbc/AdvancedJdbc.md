<a id="back-to-top"></a>

# Advanced Spring JDBC Workflows

## Menu
- [JDBC Batch Operations](#jdbc-batch-operations)
- [Generated Keys](#jdbc-generated-keys)
- [Custom Result Extraction and Callback Processing](#jdbc-custom-extraction-callbacks)
- [SimpleJdbcInsert and SimpleJdbcCall](#simple-jdbc-insert-call)
- [Embedded Databases and SQL Script Initialization](#embedded-database-sql-initialization)

## <a id="jdbc-batch-operations">JDBC Batch Operations</a>

<details>
<summary>Click for details</summary>

Batching reduces repeated client/driver round trips by submitting multiple parameter sets through JDBC's batch facilities. It is useful when many similar writes must be executed, but it is not the same thing as one atomic transaction and it does not guarantee that the database executes every row as one physical operation.

JdbcTemplate exposes batchUpdate variants for fixed or varying parameter sets. The driver and database determine details such as batch-size limits, generated-key behavior, and update counts.

~~~java
jdbcTemplate.batchUpdate(
    "insert into customer(id, name) values (?, ?)",
    customers,
    100,
    (ps, customer) -> {
        ps.setLong(1, customer.id());
        ps.setString(2, customer.name());
    });
~~~

Important distinctions:

- **batch** controls how statements are submitted;
- **transaction** controls commit/rollback boundaries;
- **chunking** controls how much work the application groups at once.

A huge batch can increase memory pressure or lock duration. Measure with the actual driver/database, use bounded batch sizes, and do not assume batching alone provides transaction semantics or error recovery.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-generated-keys">Generated Keys</a>

<details>
<summary>Click for details</summary>

Databases often generate identifiers or other values during INSERT. Spring JDBC can request those values through JDBC generated-key support, commonly using KeyHolder/GeneratedKeyHolder or JdbcClient result APIs where appropriate.

A classic template pattern is:

~~~java
KeyHolder keyHolder = new GeneratedKeyHolder();

jdbcTemplate.update(connection -> {
    PreparedStatement ps = connection.prepareStatement(
        "insert into customer(name) values (?)",
        Statement.RETURN_GENERATED_KEYS);
    ps.setString(1, name);
    return ps;
}, keyHolder);
~~~

Generated-key support depends on the JDBC driver and database. Some databases require naming the generated columns; some drivers return multiple columns or vendor-specific numeric types. Treat the returned key shape as a database/driver contract rather than assuming every generated key is a Long.

If key retrieval becomes complicated, make the required columns explicit and keep the database-specific assumption close to the DAO. The purpose of Spring's helper types is to reduce plumbing, not to erase database differences.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-custom-extraction-callbacks">Custom Result Extraction and Callback Processing</a>

<details>
<summary>Click for details</summary>

Convenience row mapping is ideal when one row maps independently to one object. Some queries do not have that shape: they may build a graph from repeated parent rows, aggregate across rows, stream values into another sink, or need custom statement configuration.

Spring's callback interfaces let the application own that special logic while JdbcTemplate still manages the surrounding JDBC workflow.

Use:

- RowMapper when each row independently creates one result element;
- ResultSetExtractor when the whole ResultSet must be considered together;
- RowCallbackHandler for per-row processing without returning a list;
- PreparedStatementCallback or related statement callbacks for lower-level execution control.

~~~java
Map<Long, OrderView> orders = jdbcTemplate.query(sql, rs -> {
    Map<Long, OrderView> result = new LinkedHashMap<>();
    while (rs.next()) {
        // merge repeated order rows into one aggregate view
    }
    return result;
});
~~~

Callbacks are an escape hatch with a defined contract, not an invitation to manually reopen resource management. Do not close the Connection or ResultSet that Spring supplied to a callback unless that callback contract explicitly assigns such ownership.

</details>

- [Back to top](#back-to-top)

---

## <a id="simple-jdbc-insert-call">SimpleJdbcInsert and SimpleJdbcCall</a>

<details>
<summary>Click for details</summary>

SimpleJdbcInsert and SimpleJdbcCall are reusable Spring JDBC helpers for operations where database metadata can remove repetitive configuration.

SimpleJdbcInsert can discover table columns through DatabaseMetaData and build the INSERT statement from a map or parameter source. It can also be configured with explicit columns and generated-key columns.

SimpleJdbcCall uses JDBC metadata to simplify stored procedure/function calls. Its metadata support depends on what the database and JDBC driver report accurately. For unsupported or unreliable metadata, declare parameters explicitly or disable metadata lookup.

~~~java
SimpleJdbcInsert insert = new SimpleJdbcInsert(dataSource)
    .withTableName("customer")
    .usingGeneratedKeyColumns("id");

Number id = insert.executeAndReturnKey(
    Map.of("name", "Ada", "status", "ACTIVE"));
~~~

These helpers are not ORM. They still operate against relational tables/procedures and use JdbcTemplate underneath. Metadata is a convenience, not a source of application truth: explicit configuration is preferable when portability, unusual schemas, or stored-procedure signatures make auto-detection ambiguous.

</details>

- [Back to top](#back-to-top)

---

## <a id="embedded-database-sql-initialization">Embedded Databases and SQL Script Initialization</a>

<details>
<summary>Click for details</summary>

Spring JDBC includes support for creating embedded databases and initializing a database from SQL scripts. These facilities are valuable for tests, samples, local bootstrap, and controlled application initialization.

EmbeddedDatabaseBuilder can configure in-process databases such as H2, HSQL, or Derby when the matching engine is available. The datasource.init support can execute schema and data scripts with configurable separators, comments, encodings, and failure behavior.

~~~java
DataSource dataSource = new EmbeddedDatabaseBuilder()
    .setType(EmbeddedDatabaseType.H2)
    .addScript("classpath:schema.sql")
    .addScript("classpath:test-data.sql")
    .build();
~~~

Keep the boundary clear: script initialization is not automatically a production schema-migration strategy. Production evolution usually needs ordered migrations, history, repeatability, rollout discipline, and compatibility management—concerns owned by tools such as Flyway/Liquibase rather than by a basic Spring JDBC bootstrap helper.

Also avoid assuming an embedded engine behaves exactly like the production database. SQL dialects, locking, types, and optimizer behavior can differ.

</details>

- [Back to top](#back-to-top)
