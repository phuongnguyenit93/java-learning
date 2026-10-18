<a id="back-to-top"></a>

# DAO and Data Access Exception Model

## Menu
- [Why Spring Uses a Consistent Data Access Exception Model](#data-access-exception-model)
- [Major DataAccessException Categories](#data-access-exception-categories)
- [JDBC and R2DBC Exception Translation](#jdbc-r2dbc-exception-translation)
- [@Repository and Persistence Exception Translation](#repository-exception-translation)
- [Handling and Propagating Data Access Failures](#data-access-failure-handling)

## <a id="data-access-exception-model">Why Spring Uses a Consistent Data Access Exception Model</a>

<details>
<summary>Click for details</summary>

Database APIs expose failures in technology-specific forms: JDBC primarily uses SQLException, R2DBC uses R2dbcException, and ORM products add their own exception families. If service code depends directly on those types, persistence technology leaks upward and callers need different handling policies for failures that mean the same thing.

Spring's answer is the unchecked org.springframework.dao.DataAccessException hierarchy. It describes failures by **data-access meaning** rather than by one driver API. Spring JDBC, Spring R2DBC, and supported persistence integrations translate native exceptions into that common vocabulary.

Unchecked does not mean "ignore errors". It means application code is not forced to catch or declare every database failure. A boundary that can make a useful decision may handle a specific subtype; otherwise the exception can propagate to transaction or application error handling.

The hierarchy preserves the original cause, so vendor diagnostics remain available for logging and debugging. Translation adds a portable classification without discarding low-level evidence.

</details>

- [Back to top](#back-to-top)

---

## <a id="data-access-exception-categories">Major DataAccessException Categories</a>

<details>
<summary>Click for details</summary>

DataAccessException is intentionally a hierarchy rather than a single wrapper. The categories let code distinguish whether retrying might make sense, whether the operation violated data constraints, or whether the result did not match the caller's expectation.

Important conceptual groups include:

- TransientDataAccessException: a failure that may succeed if retried without changing the request, such as some locking or resource conditions.
- NonTransientDataAccessException: retrying the same operation unchanged is not expected to fix the problem.
- RecoverableDataAccessException: recovery may be possible after an explicit recovery step.
- DataIntegrityViolationException: a broader integrity-constraint problem; DuplicateKeyException is a more specific case.
- PessimisticLockingFailureException and CannotAcquireLockException: locking-related failures.
- QueryTimeoutException: the operation exceeded its configured timeout.
- IncorrectResultSizeDataAccessException: actual result cardinality does not match what the caller requested.

Do not code against a very specific subtype unless the application has a real policy for that condition. A portable service handles the narrowest **meaningful** Spring exception and leaves vendor-specific diagnosis to logs and the original cause.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-r2dbc-exception-translation">JDBC and R2DBC Exception Translation</a>

<details>
<summary>Click for details</summary>

For JDBC, SQLExceptionTranslator converts SQLException into DataAccessException. Since Spring Framework 6.0, the default path uses SQLExceptionSubclassTranslator, which recognizes JDBC 4 exception subclasses and falls back to SQL-state analysis. Vendor error-code translation through SQLErrorCodeSQLExceptionTranslator remains available when an application needs that precision.

JdbcTemplate applies translation around the standard JDBC workflow, so callers normally see Spring exceptions instead of raw SQLException.

For R2DBC, Spring translates R2dbcException into the same DAO hierarchy. DatabaseClient performs this translation for its core workflow, and ConnectionFactoryUtils provides the corresponding lower-level conversion helper.

~~~text
native driver exception
        ↓
Spring translator
        ↓
DataAccessException subtype
        ↓
service/application boundary
~~~

Translation is classification, not a guarantee that every vendor exposes identical diagnostic detail. Keep the original exception as the cause when troubleshooting vendor-specific behavior, and avoid branching application logic on raw vendor codes unless portability is intentionally being traded away.

</details>

- [Back to top](#back-to-top)

---

## <a id="repository-exception-translation">@Repository and Persistence Exception Translation</a>

<details>
<summary>Click for details</summary>

@Repository serves two related roles: it is a persistence stereotype and it marks eligible beans for Spring's persistence-exception translation infrastructure. The annotation by itself does not catch exceptions.

When PersistenceExceptionTranslationPostProcessor is registered, it adds a PersistenceExceptionTranslationAdvisor to suitable @Repository beans. The advisor uses discovered PersistenceExceptionTranslator implementations to translate supported native persistence exceptions into Spring's DataAccessException hierarchy.

This is especially relevant at ORM or other persistence integration boundaries. Spring JDBC and DatabaseClient already perform their own translation in their normal workflows, so adding @Repository is not what makes JdbcTemplate translation work.

~~~text
@Repository
    → identifies persistence bean

PersistenceExceptionTranslationPostProcessor
    → applies advisor/proxy where eligible

PersistenceExceptionTranslator
    → converts supported native persistence failure
~~~

Because the mechanism is proxy/advisor based, it belongs to the integration boundary between Spring's DAO contract and a persistence implementation. Detailed ORM mapping and generic AOP mechanics remain in their owning modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="data-access-failure-handling">Handling and Propagating Data Access Failures</a>

<details>
<summary>Click for details</summary>

Handle a data-access failure only where the application can make a better decision than "log and rethrow". Examples include mapping a duplicate business key to a domain conflict, retrying a documented transient lock failure, or converting an expected result-cardinality condition into an application-specific outcome.

Avoid broad catch-and-wrap patterns that erase useful classification:

~~~java
try {
    repository.save(value);
}
catch (DataAccessException ex) {
    throw new RuntimeException("database failed", ex);
}
~~~

Prefer explicit policy at a meaningful boundary:

~~~java
try {
    jdbcClient.sql(sql).params(params).update();
}
catch (DuplicateKeyException ex) {
    throw new CustomerAlreadyExists(customerId, ex);
}
~~~

Even then, the mapping must reflect a real business contract. Do not assume every DataIntegrityViolationException means a duplicate key, and do not blindly retry every TransientDataAccessException; retries require idempotency, backoff, and a bounded policy.

The common exception model is most useful when upper layers reason about **meaning** while infrastructure logs retain the native cause for diagnosis.

</details>

- [Back to top](#back-to-top)
