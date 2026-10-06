<a id="back-to-top"></a>

# Spring JDBC Core

## Menu
- [DataSource and the Spring JDBC Workflow](#jdbc-datasource-workflow)
- [JdbcClient and the Template Stack](#jdbc-client-template-stack)
- [Positional and Named SQL Parameter Binding](#jdbc-parameter-binding)
- [Query, Update, and Callback-Based Execution](#jdbc-query-update-execution)
- [Row Mapping and Result Cardinality](#jdbc-row-mapping-cardinality)
- [Choosing JdbcClient or the Template APIs](#jdbc-client-vs-template)

## <a id="jdbc-datasource-workflow">DataSource and the Spring JDBC Workflow</a>

<details>
<summary>Click for details</summary>

A DataSource is the standard JDBC entry point that supplies Connections. Spring JDBC deliberately builds on that contract instead of inventing a separate connection API. In production, the DataSource is commonly backed by a pool; Spring JDBC does not own pool sizing, validation, or vendor-driver tuning.

JdbcTemplate organizes the repetitive JDBC workflow around the DataSource:

~~~text
obtain Connection
    ↓
prepare statement / bind parameters
    ↓
execute
    ↓
extract rows or update count
    ↓
translate SQLException
    ↓
release Connection appropriately
~~~

The word "appropriately" matters. When Spring transaction synchronization is active, the Connection can be associated with the current transaction rather than physically closed after each template call. JdbcTemplate uses Spring's resource utilities internally so callers do not need custom transaction-aware connection code.

The application still owns the SQL and the mapping decision. Spring owns the workflow that makes those decisions safe and repeatable.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-client-template-stack">JdbcClient and the Template Stack</a>

<details>
<summary>Click for details</summary>

Spring JDBC has several layers because common operations and low-level extension points need different APIs.

- JdbcTemplate is the central imperative JDBC delegate. It exposes broad query/update/execute operations plus callback contracts.
- NamedParameterJdbcTemplate wraps a JdbcTemplate and expands Spring-style named parameters to JDBC positional placeholders before execution.
- JdbcClient, introduced in Spring Framework 6.1, provides one fluent facade for common query and update operations with either positional or named parameters.

JdbcClient does not replace the lower layers. Its implementation delegates actual work to JdbcTemplate and NamedParameterJdbcTemplate.

~~~java
JdbcClient client = JdbcClient.create(dataSource);

Customer customer = client.sql(
        "select id, name from customer where id = :id")
    .param("id", id)
    .query(Customer.class)
    .single();
~~~

Use the fluent facade when it expresses the operation clearly. Reach for template callbacks or specialized helpers when the operation needs capabilities outside JdbcClient's common query/update scope.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-parameter-binding">Positional and Named SQL Parameter Binding</a>

<details>
<summary>Click for details</summary>

Parameter binding separates SQL structure from runtime values. This is both safer and clearer than assembling value text into SQL strings.

With classic JDBC-style placeholders, values are positional:

~~~java
jdbcClient.sql("select * from customer where status = ? and region = ?")
    .params("ACTIVE", "APAC")
    .query(Customer.class)
    .list();
~~~

With Spring-style named parameters, the SQL names the role of each value:

~~~java
jdbcClient.sql("""
    select * from customer
    where status = :status and region = :region
    """)
    .param("status", "ACTIVE")
    .param("region", "APAC")
    .query(Customer.class)
    .list();
~~~

NamedParameterJdbcTemplate and JdbcClient expand named parameters before the JDBC statement executes. Named parameters are not a new database protocol; the eventual JDBC driver still receives JDBC-compatible bind positions.

Binding values is not a substitute for validating dynamic SQL identifiers. Table names, column names, sort directions, and SQL fragments generally cannot be treated as ordinary bind values. If those elements are dynamic, choose them from controlled application values rather than concatenating untrusted input.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-query-update-execution">Query, Update, and Callback-Based Execution</a>

<details>
<summary>Click for details</summary>

Spring JDBC distinguishes the shape of the operation rather than hiding all database work behind one method.

**Queries** produce rows that application code maps or extracts. **Updates** return an affected-row count. More specialized operations can use callbacks when the ordinary query/update shape is not enough.

JdbcTemplate's callback model is important because it exposes controlled access to JDBC objects without giving up Spring's resource and exception workflow. Examples include PreparedStatementCreator, PreparedStatementSetter, ResultSetExtractor, RowCallbackHandler, and PreparedStatementCallback.

~~~java
int updated = jdbcClient.sql(
        "update customer set status = :status where id = :id")
    .param("status", "SUSPENDED")
    .param("id", id)
    .update();
~~~

For a query:

~~~java
List<Customer> customers = jdbcTemplate.query(
    "select id, name from customer where active = ?",
    customerRowMapper,
    true);
~~~

Choose the smallest abstraction that expresses the operation. Dropping to callbacks is useful for uncommon JDBC behavior, not a default style for simple reads and writes.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-row-mapping-cardinality">Row Mapping and Result Cardinality</a>

<details>
<summary>Click for details</summary>

A query has two independent decisions: **how each row becomes a value** and **how many values the caller expects**.

RowMapper handles one row at a time. ResultSetExtractor can consume the entire ResultSet when mapping needs cross-row state. RowCallbackHandler processes rows for side effects or streaming-style consumption without building a return collection.

JdbcClient also offers common mapping shortcuts, including target-class mapping for straightforward records or JavaBean-style objects, while allowing an explicit RowMapper when control matters.

Cardinality should be part of the contract:

- list() means zero or more rows;
- optional() means zero or one;
- single() means exactly one.

If a single-result API sees the wrong number of rows, Spring reports a DataAccessException subtype such as IncorrectResultSizeDataAccessException instead of silently picking one row.

Do not use "single row" methods simply because test data currently contains one row. The SQL predicate and database constraints should justify the cardinality expectation.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-client-vs-template">Choosing JdbcClient or the Template APIs</a>

<details>
<summary>Click for details</summary>

Prefer JdbcClient for ordinary parameterized queries and updates when its fluent API keeps SQL, bindings, mapping, and result cardinality together. It is especially convenient in Spring Framework 6.1 code that would otherwise switch between JdbcTemplate and NamedParameterJdbcTemplate for common operations.

JdbcClient itself exposes RowMapper, ResultSetExtractor, RowCallbackHandler, and generated-key update variants, so those capabilities alone are not a reason to drop to a template. Prefer JdbcTemplate or NamedParameterJdbcTemplate when you need batch APIs, PreparedStatementCreator/PreparedStatementCallback-style statement control, execute-style operations, template-specific configuration, or integration with existing code built around those types. Prefer SimpleJdbcInsert or SimpleJdbcCall when metadata-assisted insert/stored-procedure support is the real abstraction you need.

The choice is not "modern API versus obsolete API." JdbcClient is intentionally a facade over the template infrastructure.

~~~text
common query/update
    → JdbcClient first

batch / statement-creation / execute-style control
    → template APIs

metadata-assisted insert/procedure
    → SimpleJdbcInsert / SimpleJdbcCall
~~~

A team can use these APIs together. Consistency matters, but forcing every operation through one facade is less valuable than choosing the clearest Spring abstraction for the required JDBC capability.

</details>

- [Back to top](#back-to-top)
