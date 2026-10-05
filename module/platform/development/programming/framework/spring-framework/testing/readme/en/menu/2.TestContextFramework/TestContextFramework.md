<a id="back-to-top"></a>

# Spring TestContext Framework

## Menu
- [Role of the TestContext Framework](#testcontext-framework-role)
- [TestContext, TestContextManager, and core abstractions](#testcontext-core-abstractions)
- [Bootstrapping the TestContext Framework](#testcontext-bootstrap)
- [SmartContextLoader and context-loading strategy](#smart-context-loading)
- [TestExecutionListener model](#test-execution-listeners)
- [SpringExtension and test-framework integration](#test-framework-integration)

## <a id="testcontext-framework-role">Role of the TestContext Framework</a>

<details>
<summary>Click for details</summary>

The TestContext Framework is Spring's adapter layer between a general-purpose test engine and Spring-managed infrastructure. It does not replace JUnit or TestNG. Instead, it supplies lifecycle services that those engines can call into: context loading and caching, dependency injection, transaction handling, SQL scripts, event recording, and listener-based extensions.

The framework is intentionally engine-neutral. The same core abstractions can be driven by JUnit Jupiter through `SpringExtension`, by older JUnit integrations, or by TestNG support.

Think of TestContext as the **control plane** for Spring integration tests. The test engine decides *when* a test lifecycle phase occurs; TestContext decides which Spring-specific work should happen at that phase.

</details>

- [Back to top](#back-to-top)

---

## <a id="testcontext-core-abstractions">TestContext, TestContextManager, and core abstractions</a>

<details>
<summary>Click for details</summary>

`TestContext` represents the current Spring testing state: test class, current test instance and method, current exception when relevant, attributes shared by listeners, and access to the test `ApplicationContext`.

`TestContextManager` coordinates the lifecycle. It owns the active `TestContext` and invokes registered `TestExecutionListener` instances around lifecycle phases such as:

- before/after test class;
- preparing the test instance;
- before/after test method;
- before/after test execution.

The manager is the object a test-engine integration delegates to. This separation is why Spring can add behavior without teaching JUnit about Spring internals.

The `ApplicationContext` is typically loaded lazily through a cache-aware delegate. Asking the `TestContext` for the context may therefore resolve an existing cached context instead of constructing a new one.

</details>

- [Back to top](#back-to-top)

---

## <a id="testcontext-bootstrap">Bootstrapping the TestContext Framework</a>

<details>
<summary>Click for details</summary>

Bootstrapping decides **which TestContext implementation, context loader, context customizers, and listeners** will participate for a test class.

The central SPI is `TestContextBootstrapper`. `TestContextManager` obtains a bootstrapper for the test class and asks it to build the `TestContext` and listener list. A custom strategy can be selected with `@BootstrapWith`.

In ordinary tests you should not implement this SPI. Spring's default bootstrappers cover the normal cases. Web tests use a web-aware bootstrapper when `@WebAppConfiguration` is present.

Low-level custom bootstrapping is appropriate for framework authors who need to replace major TestContext strategies. Application code usually does not need to go that far. In Spring Framework 6.1, `@ContextCustomizerFactories` can register additional `ContextCustomizerFactory` implementations for a test class while retaining the normal bootstrap path; its factories are inheritable and can be merged with the defaults.

For ordinary application tests, prefer normal configuration annotations such as `@ContextConfiguration`, `@ActiveProfiles`, `@TestPropertySource`, and `@ContextCustomizerFactories` when a customizer factory is genuinely needed.

### References

- Spring Framework 6.1.14 API — `TestContextBootstrapper`, `DefaultBootstrapContext`, and `@BootstrapWith`.
- [Spring Framework 6.1.14 API — @ContextCustomizerFactories](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/ContextCustomizerFactories.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="smart-context-loading">SmartContextLoader and context-loading strategy</a>

<details>
<summary>Click for details</summary>

`SmartContextLoader` is the strategy that turns test configuration metadata into an `ApplicationContext`. Compared with the older `ContextLoader` contract, it understands both resource locations and component classes and operates on the merged configuration model used by TestContext.

Its two important responsibilities are:

1. **process configuration** before loading, including default detection when locations or classes are omitted;
2. **load the context** from the resulting `MergedContextConfiguration`.

The default delegating loader chooses an annotation-config path or an XML/Groovy resource path according to the metadata it receives. Web tests use the web-aware equivalent.

The loader does not decide test lifecycle or cache policy. It is one participant in a larger pipeline: merged configuration becomes a cache key, and a cache-aware delegate either reuses a matching context or asks the loader to create one.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-execution-listeners">TestExecutionListener model</a>

<details>
<summary>Click for details</summary>

`TestExecutionListener` is the extension mechanism that attaches Spring behavior to test lifecycle phases. Spring ships listeners for dependency injection, `@DirtiesContext`, transactions, SQL scripts, application events, and other concerns.

Default listeners are discovered and ordered by Spring infrastructure. Ordering matters because one listener may rely on state prepared by another. Custom listeners should therefore implement `Ordered` or use `@Order` rather than assuming registration order is execution order.

`@TestExecutionListeners` can register listeners for a test hierarchy. Its merge mode is important:

- `REPLACE_DEFAULTS` replaces the default list when applicable;
- `MERGE_WITH_DEFAULTS` merges local listeners with defaults, removes duplicates, and sorts the result.

Replacing defaults casually is risky: removing the dependency-injection or transactional listener can silently change what a test actually exercises.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-framework-integration">SpringExtension and test-framework integration</a>

<details>
<summary>Click for details</summary>

`SpringExtension` is Spring's JUnit Jupiter integration. It connects Jupiter lifecycle callbacks to a `TestContextManager` and also participates in dependency/parameter resolution.

This means Jupiter still owns test discovery, nested tests, repeated/parameterized execution, assertions, and extension ordering. Spring only adds Spring-specific work around those phases.

A typical integration test:

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class PricingServiceTests {

    @Autowired
    PricingService pricingService;
}
```

Composed annotations can hide the explicit `@ExtendWith`. The important point is conceptual: `SpringExtension` is an adapter into TestContext, not an alternative test engine.

</details>

- [Back to top](#back-to-top)
