<a id="back-to-top"></a>

# Transactional and Database Testing

## Menu
- [Test-managed transactions](#test-managed-transactions)
- [Resolving the transaction manager for tests](#transaction-manager-resolution)
- [Transactional test semantics and supported attributes](#transactional-test-semantics)
- [Default rollback, commit, and rollback overrides](#rollback-and-commit)
- [Programmatic transaction control with TestTransaction](#testtransaction-programmatic-control)
- [BeforeTransaction and AfterTransaction callbacks](#transaction-lifecycle-callbacks)
- [Database fixtures with @Sql](#sql-test-fixtures)
- [JDBC testing utilities](#jdbc-test-utilities)
- [Transactional testing pitfalls and false positives](#transaction-testing-pitfalls)

## <a id="test-managed-transactions">Test-managed transactions</a>

<details>
<summary>Click for details</summary>

The TestContext framework can wrap a test method in a **test-managed transaction**. `TransactionalTestExecutionListener`, which is enabled by default in the standard listener set, detects Spring's `@Transactional` on the test class or test method, starts the transaction before the test method runs, and ends it after the test completes. The default end behavior is rollback.

This transaction belongs to the testing infrastructure. It is different from a Spring-managed transaction opened by application code and from a transaction managed directly by the application through an API. Code invoked by the test can still participate in the test-managed transaction when its normal transaction propagation allows it, because the transaction is bound to the test thread through Spring's transaction infrastructure.

The distinction matters when reading failures. A repository method annotated with `@Transactional` is exercising application transaction semantics owned by the transaction-management module. The testing module owns only the outer test boundary, its rollback/commit policy, and the utilities that let a test interact with that boundary.

### References

- [Spring Framework 6.1.14 API — TransactionalTestExecutionListener](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/transaction/TransactionalTestExecutionListener.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-manager-resolution">Resolving the transaction manager for tests</a>

<details>
<summary>Click for details</summary>

A transactional test needs a `PlatformTransactionManager` from the test's `ApplicationContext`. When `@Transactional` names a manager through `value` or `transactionManager`, the listener uses that qualifier/name. This is the clearest choice when the context contains multiple transaction managers with different responsibilities.

Without an explicit qualifier, Spring follows its TestContext transaction-manager lookup conventions. It can obtain the manager selected by a `TransactionManagementConfigurer`, resolve a single manager by type, honor a primary candidate, or fall back to the conventional bean name `transactionManager`. An ambiguous context should be made explicit instead of relying on accidental bean ordering.

The test transaction should use the manager that coordinates the resources exercised by the test. Choosing a manager is a testing-infrastructure concern here; propagation, isolation, synchronization, and the deeper behavior of the manager remain the responsibility of the transaction-management module.

### References

- [Spring Framework 6.1.14 API — TestContextTransactionUtils](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/transaction/TestContextTransactionUtils.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="transactional-test-semantics">Transactional test semantics and supported attributes</a>

<details>
<summary>Click for details</summary>

`@Transactional` on a test does not expose the full application transaction contract. For test-managed transactions, `value`/`transactionManager` are supported to select the manager. The only propagation values with special supported meaning are `NOT_SUPPORTED` and `NEVER`, which cause the test to run without a test-managed transaction.

Attributes such as `isolation`, `timeout`, `readOnly`, `rollbackFor`, `rollbackForClassName`, `noRollbackFor`, and `noRollbackForClassName` are not used to configure test-managed transaction behavior. Use `@Rollback`, `@Commit`, or `TestTransaction` for the test's commit/rollback decision. If application code itself declares those attributes, that application transaction policy is a separate concern.

Method-level test lifecycle callbacks such as JUnit Jupiter `@BeforeEach` and `@AfterEach` execute within the test-managed transaction. Class- or suite-level callbacks such as `@BeforeAll` and `@AfterAll` do not. `@Transactional` is therefore meant for test methods/classes rather than for those lifecycle methods.

### References

- [Spring Framework 6.1 Reference — Transaction Management in the TestContext Framework](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="rollback-and-commit">Default rollback, commit, and rollback overrides</a>

<details>
<summary>Click for details</summary>

When the TestContext framework starts a transaction for a test, it marks that transaction for rollback by default. This gives database-oriented integration tests a useful isolation property: data written by the test is normally discarded when the test-managed transaction ends.

`@Rollback(false)` changes that policy to commit, and `@Commit` is a semantic shortcut for the same intent. These annotations can define a class-level default and can be overridden more narrowly at the method level. Committing should be explicit because it changes durable state that later tests may observe.

Default rollback is not proof that every side effect has been isolated. Work performed on another thread, in another process, or in an isolated transaction can commit independently of the test-managed transaction. Assertions should therefore match the actual transaction boundary instead of assuming that the annotation automatically cleans every resource touched by the scenario.

</details>

- [Back to top](#back-to-top)

---

## <a id="testtransaction-programmatic-control">Programmatic transaction control with TestTransaction</a>

<details>
<summary>Click for details</summary>

`TestTransaction` gives a test direct control over the current **test-managed** transaction without replacing the application's transaction APIs. `isActive()` lets the test verify whether a test transaction exists. `flagForRollback()` and `flagForCommit()` change how the current transaction will end.

`end()` immediately ends the active test transaction according to its current rollback/commit flag. This is useful when the test must prove behavior after a real commit or after a rollback before the test method itself finishes. `start()` can then begin a new test-managed transaction, provided the test is configured transactionally and no test transaction is already active.

For example, a test can write data, call `flagForCommit()`, `end()`, verify behavior that requires committed data, and then `start()` a fresh transaction for additional assertions or cleanup. This should remain focused: complex transaction choreography in a test can obscure the application behavior being verified.

### References

- [Spring Framework 6.1.14 API — TestTransaction](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/transaction/TestTransaction.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-lifecycle-callbacks">BeforeTransaction and AfterTransaction callbacks</a>

<details>
<summary>Click for details</summary>

`@BeforeTransaction` and `@AfterTransaction` are callbacks around the **test-managed transaction boundary**. For a transactional test, methods annotated with `@BeforeTransaction` run before Spring starts the test transaction, and `@AfterTransaction` methods run after Spring has ended it. They are therefore the right place for assertions or setup that must observe database state outside the rollback-managed test transaction.

This differs from normal per-test lifecycle methods. A JUnit Jupiter `@BeforeEach` or `@AfterEach` method is inside the test-managed transaction, while `@BeforeTransaction` and `@AfterTransaction` deliberately sit outside it. If a test is not configured to run transactionally, these transaction callbacks are not invoked for that test.

Spring Framework 6.1 also allows these callbacks, when used with `SpringExtension` and JUnit Jupiter, to declare parameters that registered JUnit `ParameterResolver` extensions can resolve. That includes common Jupiter parameters and Spring-resolved dependencies. In inheritance, superclass/interface `@BeforeTransaction` callbacks run before callbacks in the current class; `@AfterTransaction` callbacks unwind in the opposite direction.

The practical keywords are `@BeforeTransaction`, `@AfterTransaction`, the transactional test boundary, and the final rollback/commit behavior. Use them when the assertion specifically needs to cross that boundary.

### References

- [Spring Framework 6.1.14 API — BeforeTransaction](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/transaction/BeforeTransaction.html)
- [Spring Framework 6.1.14 API — AfterTransaction](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/transaction/AfterTransaction.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="sql-test-fixtures">Database fixtures with @Sql</a>

<details>
<summary>Click for details</summary>

`@Sql` lets a TestContext-based integration test execute SQL scripts or inline statements as part of test setup and teardown. `SqlScriptsTestExecutionListener`, which is in the default listener set, executes the declaration. By default scripts associated with a test method run before that method; `executionPhase` can also place them after the method.

Class- and method-level declarations can be combined deliberately. For the normal per-method phases, method-level `@Sql` normally overrides class-level declarations, while `@SqlMergeMode` can request merging. `@SqlConfig` controls details such as script parsing, data-source or transaction-manager selection, error handling, and transaction mode. With `INFERRED` transaction mode, scripts can participate in an existing transaction when one is present; `ISOLATED` runs them in a new transaction that is immediately committed.

Spring Framework 6.1 added class-level `BEFORE_TEST_CLASS` and `AFTER_TEST_CLASS` execution phases. These class-phase declarations always run in addition to method-level `@Sql`; they cannot be overridden by a method declaration. `BEFORE_TEST_CLASS` runs before framework-specific class lifecycle callbacks such as JUnit Jupiter `@BeforeAll` and causes the test `ApplicationContext` to be loaded eagerly when needed. `AFTER_TEST_CLASS` runs after callbacks such as `@AfterAll`. These phases are useful for expensive schema-level setup or teardown that should run once per class, but they also create state outside an individual test method's default rollback cycle.

If no script is specified, Spring supports conventional default script detection based on the test class or test method name. Explicit paths are usually easier to review in larger suites because the fixture dependency is visible at the annotation site.

### References

- [Spring Framework 6.1 Reference — Executing SQL Scripts](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-test-utilities">JDBC testing utilities</a>

<details>
<summary>Click for details</summary>

`JdbcTestUtils` contains small database-testing helpers for common assertions and cleanup tasks. It can count all rows in a table, count rows matching a `WHERE` clause, delete rows from tables or by condition, and drop tables. The operations work with Spring JDBC access objects so tests can express intent without repeating low-level counting or cleanup code.

The transactional JUnit 4 and TestNG support base classes in Spring expose convenience methods that delegate to these utilities. New tests do not need to inherit from those classes merely to use the helpers; calling `JdbcTestUtils` directly keeps the dependency explicit.

These utilities belong here because they support verification of database state. The mechanics of `JdbcClient`, `JdbcTemplate`, SQL exception translation, and general Spring data access are taught by the data-access module. A testing chapter should use those mechanisms only as much as needed to prepare or assert the fixture.

### References

- [Spring Framework 6.1.14 API — JdbcTestUtils](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/jdbc/JdbcTestUtils.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-testing-pitfalls">Transactional testing pitfalls and false positives</a>

<details>
<summary>Click for details</summary>

Rollback-by-default is convenient, but it can hide failures if the test never forces persistence work to reach the database. With ORM-based code, changes may still be buffered in the persistence context. A test that asserts only in-memory state can pass even though a flush at commit time would fail. When the scenario depends on database constraints or generated SQL, explicitly flush through the persistence technology before making the decisive assertion.

Thread boundaries are another common trap. Test-managed transactions are bound to the test thread. A preemptive timeout that runs the test body on another thread can allow database work to execute outside the transaction and commit even though Spring later rolls back the transaction on the original thread. A request sent to a live server is likewise handled on a server thread and is not automatically enclosed by the caller's test-managed transaction.

Also watch fixture transaction modes. `@Sql(transactionMode = ISOLATED)` commits its scripts independently; `TestTransaction.end()` can commit when flagged; and application code can deliberately open independent transactions. Parallel tests that mutate the same database rows add another source of interference.

A reliable transactional test therefore asks two questions: which thread/resource manager owns each write, and which transaction will actually end that write? Default rollback is useful only for work that truly participates in the test-managed transaction.

### References

- [Spring Framework 6.1 Reference — Transaction Management in Tests](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)
