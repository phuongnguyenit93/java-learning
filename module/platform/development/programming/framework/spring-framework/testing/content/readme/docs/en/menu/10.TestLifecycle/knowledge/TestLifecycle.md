<a id="back-to-top"></a>

# Lifecycle, Extensions, and Testing Strategy

## Menu
- [TestContext lifecycle callbacks](#test-lifecycle-callbacks)
- [TestExecutionListener registration and ordering](#testexecutionlistener-ordering)
- [Test execution events](#test-execution-events)
- [Recording ApplicationContext events in tests](#application-event-recording)
- [Support classes and composed testing annotations](#support-classes-and-composed-annotations)
- [Ahead-of-time support for integration tests](#aot-test-support)
- [Execution-mode constraints and parallel-safety review](#execution-mode-constraints)
- [Deciding between unit, context, mock-web, and live-server tests](#test-scope-decision)
- [Spring Framework testing strategy synthesis](#testing-strategy-synthesis)

## <a id="test-lifecycle-callbacks">TestContext lifecycle callbacks</a>

<details>
<summary>Click for details</summary>

The TestContext Framework observes the test lifecycle through `TestContextManager`. The test engine still owns the actual test lifecycle; Spring receives callbacks around key phases and lets registered `TestExecutionListener` implementations react.

The important phases include:

- before and after the test class;
- preparing the test instance;
- before and after each test method;
- before and after the actual test-method execution.

Those phases are deliberately more granular than a simple “before/after test” pair. Dependency injection belongs to test-instance preparation, while transaction setup, SQL scripts, event publication, or context invalidation need different lifecycle positions.

When debugging an integration test, ask **which lifecycle phase should have prepared the missing state?** That usually points more directly to the responsible listener than treating TestContext as one opaque block.

</details>

- [Back to top](#back-to-top)

---

## <a id="testexecutionlistener-ordering">TestExecutionListener registration and ordering</a>

<details>
<summary>Click for details</summary>

Spring's default `TestExecutionListener` set covers servlet setup, dirty-context handling, application events, dependency injection, observation support, transactions, SQL scripts, and lifecycle-event publication.

Ordering is part of the contract. A listener can require work performed by an earlier listener, so custom listeners should use `Ordered` or `@Order` instead of relying on declaration order.

With `@TestExecutionListeners`, remember the merge policy:

- `REPLACE_DEFAULTS` can remove Spring's normal listener set;
- `MERGE_WITH_DEFAULTS` combines local listeners with defaults, removes duplicates, and re-sorts them.

Replacing defaults unintentionally can disable dependency injection, transactions, SQL scripts, or event support without changing the test code itself.

### References

- [Spring Framework 6.1.14 API — TestExecutionListeners](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/TestExecutionListeners.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="test-execution-events">Test execution events</a>

<details>
<summary>Click for details</summary>

Spring can publish TestContext lifecycle phases as application events through `EventPublishingTestExecutionListener`. These events allow infrastructure or test-support components inside the `ApplicationContext` to react to test execution without defining another custom listener.

The events correspond to TestContext phases such as before/after test class, before/after test method, and before/after test execution.

There is an important timing constraint: the listener publishes through the test `ApplicationContext`. If the context has not been loaded yet, an early event cannot be published through it. The first test class that causes a fresh context load may therefore not observe every class-level “before” event in the same way a reused context can.

Likewise, if a context is dirtied before a late lifecycle event, there may no longer be an active context through which to publish that event.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-event-recording">Recording ApplicationContext events in tests</a>

<details>
<summary>Click for details</summary>

`@RecordApplicationEvents` enables recording of application events published by the test `ApplicationContext`. The test can then inject `ApplicationEvents` and make assertions about domain or application-level events produced by the scenario.

```java
@RecordApplicationEvents
class OrderEventTests {

    @Autowired
    ApplicationEvents events;
}
```

Recording belongs to each test execution rather than being one unbounded global event log. In Spring Framework 6.1.14, the recording contract covers events published from the test thread or its descendant threads; it should not be read as a guarantee that events published from arbitrary unrelated worker threads are captured. That distinction matters when application event handling crosses executor boundaries.

Synchronous listener failures propagate normally because publication happens on the calling thread. Asynchronous event-listener failures do not automatically propagate back through the TestContext lifecycle, so an event being published is not by itself proof that async processing succeeded.

### References

- [Spring Framework 6.1.14 API — RecordApplicationEvents](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/event/RecordApplicationEvents.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="support-classes-and-composed-annotations">Support classes and composed testing annotations</a>

<details>
<summary>Click for details</summary>

Spring testing annotations are designed to be composed. A project can combine recurring TestContext configuration into a domain-specific annotation instead of repeating the same annotation stack across test classes.

For example, a composed annotation can bundle `@ContextConfiguration`, `@ActiveProfiles`, `@TestPropertySource`, and a test-engine integration annotation while still preserving the semantics of the underlying Spring annotations.

Spring also provides support base classes in some testing areas, but inheritance should not become the default way to share test configuration. Composed annotations and focused fixture helpers usually create less coupling than a deep abstract-test hierarchy.

Keep composed annotations semantic. A name such as `@RepositoryIntegrationTest` should communicate what environment it establishes, not merely hide a collection of unrelated convenience settings.

</details>

- [Back to top](#back-to-top)

---

## <a id="aot-test-support">Ahead-of-time support for integration tests</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 supports ahead-of-time processing for TestContext integration tests. During build-time AOT processing, Spring discovers unique test-context configurations and prepares optimized context initialization metadata. At runtime, those AOT-processed contexts can be loaded through the TestContext cache.

Custom infrastructure has explicit AOT extension points. A custom context loader that must participate in AOT processing should implement the `AotContextLoader` contract, and a custom listener with AOT work can participate through the corresponding TestContext AOT listener SPI.

`@DisabledInAotMode` marks tests that should not run in AOT mode when their assumptions require runtime behavior that cannot be reproduced there.

Not every TestContext feature is available in AOT mode. In Spring Framework 6.1, `@ContextHierarchy` is not supported for TestContext AOT processing. That is a capability boundary, not a reason to redesign ordinary JVM tests that legitimately use a hierarchy.

### References

- [Spring Framework 6.1.14 API — AotContextLoader](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/aot/AotContextLoader.html)
- [Spring Framework 6.1.14 API — DisabledInAotMode](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/aot/DisabledInAotMode.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="execution-mode-constraints">Execution-mode constraints and parallel-safety review</a>

<details>
<summary>Click for details</summary>

Parallel test execution is controlled by the test engine/build tool, not by Spring. TestContext is designed to support concurrent execution, but the rest of the test fixture must also be safe.

Parallel execution is a poor fit when tests:

- mutate the same external database/file/message resource without isolation;
- depend on method ordering;
- frequently use `@DirtiesContext`;
- depend on thread-bound state that is not recreated on worker threads.

One test can invalidate or evict a context another concurrent test still expects to use. A custom `TestContext` implementation also needs to support the copy semantics required by concurrent TestContext execution.

Thread-bound test transactions deserve special attention. Spring binds the test-managed transaction to the prepared test thread. A testing feature that runs the test body on another thread, such as a preemptive timeout, can execute database work outside that transaction.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-scope-decision">Deciding between unit, context, mock-web, and live-server tests</a>

<details>
<summary>Click for details</summary>

Choose test scope from the strongest claim the test must prove:

```text
business logic only
→ plain object test

Spring bean graph / profiles / lifecycle
→ TestContext + ApplicationContext

Spring MVC or WebFlux request handling
→ MockMvc or mock-bound WebTestClient

real transport / deployed server behavior
→ live-server HTTP test
```

Larger scopes are not automatically better. They increase configuration, shared state, startup cost, and the number of possible causes when a test fails.

The best suite normally mixes scopes: many focused object tests, enough context tests to prove Spring wiring, targeted mock-web tests for request handling, and a smaller number of live-server tests for boundaries that cannot be simulated faithfully.

</details>

- [Back to top](#back-to-top)

---

## <a id="testing-strategy-synthesis">Spring Framework testing strategy synthesis</a>

<details>
<summary>Click for details</summary>

Spring Framework testing is a set of **evidence-producing layers**, not one testing style.

The end-to-end model is:

```text
ordinary test engine
        ↓
SpringExtension / engine adapter
        ↓
TestContextManager
        ↓
TestExecutionListeners
        ↓
MergedContextConfiguration
        ↓
context cache + ApplicationContext
        ↓
optional transaction / web / HTTP-client test infrastructure
```

The module's learning path connects these layers as follows:

- context configuration determines what Spring environment exists;
- fixtures expose that environment to tests;
- profiles/properties affect context identity;
- caching balances startup cost with isolation;
- transactional support controls the outer test transaction;
- MockMvc and WebTestClient add web-facing evidence;
- lifecycle, events, AOT, and parallel execution explain how the infrastructure behaves around the test.

Keep one decision rule: **use only the Spring test infrastructure required to prove the behavior at risk, and know which production boundary each test still leaves out.**

</details>

- [Back to top](#back-to-top)
