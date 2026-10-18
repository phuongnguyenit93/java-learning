---
video:
  url: ""
---

# Focused Data Testing with `@DataJpaTest` and `@JdbcTest`

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## What Does a Boot Data Slice Isolate?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does a Boot Data Slice Isolate?

**Time:** `00:00–00:42`

**Visual:**

Show a persistence-only context: repository/data-access components plus data infrastructure inside; web, messaging, and unrelated services outside.

**Script:**

Data slices focus the test context on persistence infrastructure and the components needed to verify data-access behavior. They deliberately leave out loading unrelated web, messaging, or service-layer infrastructure. The target is a context large enough to exercise Boot's persistence integration realistically while remaining smaller and easier to reason about than a full application context.

**Purpose:**

Explain how a Boot data slice isolates persistence behavior from unrelated web and service layers while still providing the infrastructure needed for focused database tests.

## What Does `@DataJpaTest` Configure for JPA-Focused Testing?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:42–00:57`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: draw `@DataJpaTest` around entities, repositories, JPA/Hibernate infrastructure, transaction support, and test database configuration.

**Script:**

A data slice is only useful when its exact contents are known, so start with `@DataJpaTest` and the JPA infrastructure Boot places inside that boundary.

**Purpose:**

Move from the generic data-slice boundary to the JPA-specific infrastructure that makes repository and mapping tests possible.

### Scene 1 — What Does `@DataJpaTest` Configure for JPA-Focused Testing?

**Time:** `00:57–01:50`

**Visual:**

Draw `@DataJpaTest` around entities, repositories, JPA/Hibernate infrastructure, transaction support, and test database configuration.

**Script:**

`@DataJpaTest` focuses on JPA components such as entities and repositories and imports the test auto-configuration needed for JPA-oriented persistence testing. It is designed to verify mappings, repository behavior, queries, and persistence integration without loading the full application. By default, Boot also participates in test database configuration, which can replace a regular database with an embedded test database when one is available. That behavior can be changed when the test must use a different database arrangement.

**Purpose:**

Show what `@DataJpaTest` contributes around JPA repositories, entities, transactions, and test database support so learners know the exact boundary being exercised.

## How Does the Transactional Default of `@DataJpaTest` Affect a Test?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:05`

**Visual:**

Extend the existing lifecycle/timeline into the next phase instead of resetting the diagram: show one test method inside a test-managed transaction: writes occur, assertion runs, then rollback restores isolation for the next test.

**Script:**

Because `@DataJpaTest` is transactional by default, the next consequence to understand is how rollback shapes what each test can observe.

**Purpose:**

Carry JPA slice setup into the rollback model that determines persistence-test isolation.

### Scene 1 — How Does the Transactional Default of `@DataJpaTest` Affect a Test?

**Time:** `02:05–02:54`

**Visual:**

Show one test method inside a test-managed transaction: writes occur, assertion runs, then rollback restores isolation for the next test.

**Script:**

`@DataJpaTest` is transactional by default. In the usual test-managed transaction model, changes made during a test are rolled back when the test completes, which keeps focused persistence tests isolated from one another. The detailed lifecycle of test-managed transactions belongs to Spring TestContext. The Boot-specific lesson is that the JPA slice opts into that transactional testing model by default and that real-server tests have a different thread/transaction boundary.

**Purpose:**

Make the transactional default of `@DataJpaTest` operationally clear, especially what automatic rollback does and does not prove about production transaction behavior.

## What Does `@JdbcTest` Configure for JDBC-Focused Testing?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:54–03:09`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: replace the ORM layer with `DataSource` + `JdbcTemplate`/JDBC infrastructure inside a focused `@JdbcTest` context.

**Script:**

JPA is not the only focused persistence path; `@JdbcTest` shows the same slice idea without an ORM layer.

**Purpose:**

Contrast JPA-focused persistence with the JDBC slice so technology scope drives slice selection.

### Scene 1 — What Does `@JdbcTest` Configure for JDBC-Focused Testing?

**Time:** `03:09–03:57`

**Visual:**

Replace the ORM layer with `DataSource` + `JdbcTemplate`/JDBC infrastructure inside a focused `@JdbcTest` context.

**Script:**

`@JdbcTest` supplies a focused context for JDBC-oriented data access. It auto-configures the JDBC testing infrastructure needed for components that work directly with a `DataSource`, `JdbcTemplate`, or similar relational-access facilities while excluding unrelated application layers. Choose it when the persistence behavior under test is JDBC-centric rather than JPA-centric. The slice should match the technology boundary whose configuration and interaction need to be proven.

**Purpose:**

Contrast `@JdbcTest` with JPA-focused testing by showing the JDBC infrastructure it keeps and the ORM layer it intentionally omits.

## When Does Test Database Auto-Configuration Need Customization?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:57–04:08`

**Visual:**

Carry the observable result from the previous scene into the next mechanism: compare embedded replacement with a real container database; annotate dialect, native SQL, migrations, isolation, extensions, and driver behavior as possible fidelity differences.

**Script:**

Once the slice infrastructure is chosen, database replacement becomes the next fidelity decision when the default test database no longer represents the target behavior.

**Purpose:**

Carry the chosen persistence slice into the database-fidelity decision instead of treating embedded replacement as universally representative.

### Scene 1 — When Does Test Database Auto-Configuration Need Customization?

**Time:** `04:08–05:03`

**Visual:**

Compare embedded replacement with a real container database; annotate dialect, native SQL, migrations, isolation, extensions, and driver behavior as possible fidelity differences.

**Script:**

The default embedded-test-database behavior is useful for fast focused tests, but it is not always representative of the production database. SQL dialect differences, native queries, migrations, transaction isolation, extensions, or driver behavior can require a real database instead. Boot supplies test database controls such as `@AutoConfigureTestDatabase` to tune replacement behavior. If production fidelity requires the actual service, a Testcontainers-backed service connection is often a better integration boundary than forcing every data slice to use an in-memory substitute.

**Purpose:**

Identify when default test-database replacement or embedded database assumptions must be customized to match the persistence behavior under test.

## When Should a Data Slice Hand Off to a Real-Service Integration Test?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:03–05:15`

**Visual:**

Keep the running dependency/client pipeline on screen and extend it through the next integration step: extend the persistence boundary to a real database container and show Boot service connection feeding the same application client configuration.

**Script:**

Customizing the data source still keeps the test inside a slice; when the behavior depends on the real service boundary, widen the test to real-service integration.

**Purpose:**

Escalate from database replacement settings to a real-service boundary only when product-specific database behavior is part of the risk.

### Scene 1 — When Should a Data Slice Hand Off to a Real-Service Integration Test?

**Time:** `05:15–06:03`

**Visual:**

Extend the persistence boundary to a real database container and show Boot service connection feeding the same application client configuration.

**Script:**

Hand off when the behavior depends on the real database product rather than only on JPA/JDBC integration. Examples include vendor-specific SQL, indexing behavior, extensions, migration compatibility, or connection characteristics that an embedded database cannot mirror. The test can still remain focused on persistence while using a realistic service. The later Testcontainers chapter explains how Boot's service connections can supply connection details without manually wiring every property.

**Purpose:**

Give a boundary rule for escalating from an in-process data slice to a real service or database when fidelity depends on external-system behavior.

## Where Does Boot Data-Slice Support Hand Off to Persistence and Transaction Testing Mechanics?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:03–06:15`

**Visual:**

Keep the ownership lanes visible and move the next responsibility across the correct boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

That escalation reveals the final ownership split between Boot's slice setup and the persistence/transaction mechanics underneath it.

**Purpose:**

Finish the current mechanism by assigning the next responsibility to the framework or library that actually owns it.

### Scene 1 — Where Does Boot Data-Slice Support Hand Off to Persistence and Transaction Testing Mechanics?

**Time:** `06:15–07:05`

**Visual:**

Use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

On the Boot side, the responsibility is the slice annotation, selected test auto-configuration, test-database integration, and Boot-specific service connection support. JPA mapping semantics, JDBC APIs, repository implementation details, database transaction semantics, and TestContext's transaction lifecycle belong to their respective modules. Use this boundary when diagnosing failures: determine first whether Boot assembled the intended test context, then move to the persistence technology or transaction owner for deeper behavior.

**Purpose:**

Separate Boot data-slice assembly from persistence-framework and Spring transaction mechanics so failures are routed to the layer that actually owns them.