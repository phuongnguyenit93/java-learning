<a id="back-to-top"></a>

# Focused Data Testing with `@DataJpaTest` and `@JdbcTest`

## Menu
- [What Does a Boot Data Slice Isolate?](#data-slice-purpose)
- [What Does `@DataJpaTest` Configure for JPA-Focused Testing?](#datajpa-test-model)
- [How Does the Transactional Default of `@DataJpaTest` Affect a Test?](#datajpa-transaction-model)
- [What Does `@JdbcTest` Configure for JDBC-Focused Testing?](#jdbc-test-model)
- [When Does Test Database Auto-Configuration Need Customization?](#data-test-database-customization)
- [When Should a Data Slice Hand Off to a Real-Service Integration Test?](#data-slice-integration-handoff)
- [Where Does Boot Data-Slice Support Hand Off to Persistence and Transaction Testing Mechanics?](#data-testing-framework-boundary)

## <a id="data-slice-purpose">What Does a Boot Data Slice Isolate?</a>

<details>
<summary>Click for details</summary>
Data slices focus the test context on persistence infrastructure and the components needed to verify data-access behavior. They intentionally avoid loading unrelated web, messaging, or service-layer infrastructure.

The goal is a context large enough to exercise Boot's persistence integration realistically while remaining smaller and easier to reason about than a full application context.

### References

- [Spring Boot 3.3 — Test Slices](https://docs.spring.io/spring-boot/3.3/appendix/test-auto-configuration/slices.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="datajpa-test-model">What Does `@DataJpaTest` Configure for JPA-Focused Testing?</a>

<details>
<summary>Click for details</summary>
`@DataJpaTest` focuses on JPA components such as entities and repositories and imports the test auto-configuration needed for JPA-oriented persistence testing. It is designed to verify mappings, repository behavior, queries, and persistence integration without loading the full application.

By default, Boot also participates in test database configuration, which can replace a regular database with an embedded test database when one is available. That behavior can be changed when the test must use a different database arrangement.

</details>

- [Back to top](#back-to-top)

---

## <a id="datajpa-transaction-model">How Does the Transactional Default of `@DataJpaTest` Affect a Test?</a>

<details>
<summary>Click for details</summary>
`@DataJpaTest` is transactional by default. In the usual test-managed transaction model, changes made during a test are rolled back when the test completes, which keeps focused persistence tests isolated from one another.

The detailed lifecycle of test-managed transactions belongs to Spring TestContext. The Boot-specific lesson is that the JPA slice opts into that transactional testing model by default and that real-server tests have a different thread/transaction boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdbc-test-model">What Does `@JdbcTest` Configure for JDBC-Focused Testing?</a>

<details>
<summary>Click for details</summary>
`@JdbcTest` provides a focused context for JDBC-oriented data access. It auto-configures the JDBC testing infrastructure needed for components that work directly with a `DataSource`, `JdbcTemplate`, or similar relational-access facilities while excluding unrelated application layers.

Choose it when the persistence behavior under test is JDBC-centric rather than JPA-centric. The slice should match the technology boundary whose configuration and interaction need to be proven.

</details>

- [Back to top](#back-to-top)

---

## <a id="data-test-database-customization">When Does Test Database Auto-Configuration Need Customization?</a>

<details>
<summary>Click for details</summary>
The default embedded-test-database behavior is useful for fast focused tests, but it is not always representative of the production database. SQL dialect differences, native queries, migrations, transaction isolation, extensions, or driver behavior can require a real database instead.

Boot provides test database controls such as `@AutoConfigureTestDatabase` to tune replacement behavior. If production fidelity requires the actual service, a Testcontainers-backed service connection is often a better integration boundary than forcing every data slice to use an in-memory substitute.

</details>

- [Back to top](#back-to-top)

---

## <a id="data-slice-integration-handoff">When Should a Data Slice Hand Off to a Real-Service Integration Test?</a>

<details>
<summary>Click for details</summary>
Hand off when the behavior depends on the real database product rather than only on JPA/JDBC integration. Examples include vendor-specific SQL, indexing behavior, extensions, migration compatibility, or connection characteristics that an embedded database cannot reproduce.

The test can still remain focused on persistence while using a realistic service. The later Testcontainers chapter explains how Boot's service connections can supply connection details without manually wiring every property.

</details>

- [Back to top](#back-to-top)

---

## <a id="data-testing-framework-boundary">Where Does Boot Data-Slice Support Hand Off to Persistence and Transaction Testing Mechanics?</a>

<details>
<summary>Click for details</summary>
Boot owns the slice annotation, selected test auto-configuration, test-database integration, and Boot-specific service connection support. JPA mapping semantics, JDBC APIs, repository implementation details, database transaction semantics, and TestContext's transaction lifecycle belong to their respective modules.

Use this boundary when diagnosing failures: determine first whether Boot assembled the intended test context, then move to the persistence technology or transaction owner for deeper behavior.

</details>

- [Back to top](#back-to-top)
