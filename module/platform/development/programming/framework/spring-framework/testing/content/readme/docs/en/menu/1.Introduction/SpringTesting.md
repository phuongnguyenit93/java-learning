<a id="back-to-top"></a>

# Spring Testing

## Menu
- [Why Spring testing support exists](#spring-testing-purpose)
- [Testability through IoC and ordinary object construction](#testability-through-ioc)
- [Unit tests versus Spring integration tests](#unit-vs-integration-tests)
- [Choosing the smallest useful test scope](#test-scope-ladder)
- [Spring Framework testing boundaries](#testing-boundaries)

## <a id="spring-testing-purpose">Why Spring testing support exists</a>

<details>
<summary>Click for details</summary>

Most Java code can be tested without Spring. The testing support in `spring-test` exists for the cases where the behavior being verified depends on Spring itself: bean wiring, application-context configuration, transaction test support, MVC dispatching, WebFlux handlers, or an HTTP client collaborator.

The key idea is **not** “use Spring for every test.” Spring testing infrastructure is an escalation tool. Start with ordinary object tests; introduce the TestContext Framework or web-test infrastructure only when the missing Spring runtime behavior is part of the claim the test must prove.

That distinction keeps tests fast and precise. A service method that only depends on an interface normally needs no `ApplicationContext`. A test that claims a profile selects the correct bean, a controller is mapped through the real MVC configuration, or a transaction is rolled back by Spring does need Spring-managed infrastructure.

Throughout this module, ask one question first: **what Spring behavior must this test observe that a plain object test cannot prove?**

</details>

- [Back to top](#back-to-top)

---

## <a id="testability-through-ioc">Testability through IoC and ordinary object construction</a>

<details>
<summary>Click for details</summary>

Spring's IoC style improves testability before any testing framework is involved. Constructor injection makes dependencies explicit, so application objects can often be instantiated directly with fakes, stubs, or mocks.

```java
PaymentService service =
        new PaymentService(fakeGateway, fixedClock);

Receipt receipt = service.pay(order);
```

No container is required if the behavior under test is entirely inside `PaymentService` and its collaborators. Loading an `ApplicationContext` in such a test adds startup work and configuration coupling without increasing the evidence.

This is an important mental model for Spring developers: **IoC is a design property; TestContext is an integration-testing facility.** The fact that production uses Spring does not imply every test should start Spring.

Use Spring-managed tests when you need to verify things such as component registration, qualifiers, profiles, post-processors, proxies, scoped beans, transaction listeners, or web infrastructure that only exists after the container is configured.

</details>

- [Back to top](#back-to-top)

---

## <a id="unit-vs-integration-tests">Unit tests versus Spring integration tests</a>

<details>
<summary>Click for details</summary>

A plain unit test controls object construction itself. Its failures usually point to the code under test or to explicitly supplied collaborators. A Spring integration test asks a broader question: **does this code behave correctly when Spring creates and connects the participating infrastructure?**

The TestContext Framework sits around the external test engine and manages Spring-specific concerns such as context loading and caching, dependency injection into the test instance, test-managed transactions, and lifecycle listeners. JUnit or TestNG still owns test discovery and execution.

The distinction is about evidence, not annotations. A JUnit test that manually creates a controller is still a unit-style test. A JUnit test using `SpringExtension` and `@ContextConfiguration` is a Spring integration test because its result depends on a Spring-managed context.

Avoid treating “integration test” as “slow test.” A cached TestContext test may be fast, while a poorly isolated unit test may be slow. Scope should be chosen from the behavior being verified, not from a naming convention.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-scope-ladder">Choosing the smallest useful test scope</a>

<details>
<summary>Click for details</summary>

A useful testing ladder is:

```text
plain object test
    ↓ when container behavior matters
TestContext + ApplicationContext
    ↓ when web dispatching matters
MockMvc / mock-bound WebTestClient
    ↓ when the real HTTP stack matters
live-server HTTP test
```

Each step adds infrastructure and therefore adds both evidence and cost. For example, `MockMvc` can prove Spring MVC mapping, binding, validation, filters, and exception resolution without opening a socket. A live-server test additionally proves the real network/server boundary but is more expensive and usually harder to diagnose.

Choose the **lowest rung that observes the behavior at risk**. If a bug can only come from MVC configuration, a plain controller unit test is too small. If the code is a calculation with no Spring dependency, an application-context test is too large.

This ladder also prevents duplicated confidence. A few broad integration tests can verify configuration while many focused object tests cover branching logic quickly.

</details>

- [Back to top](#back-to-top)

---

## <a id="testing-boundaries">Spring Framework testing boundaries</a>

<details>
<summary>Click for details</summary>

This module owns the testing infrastructure supplied by Spring Framework: TestContext, context loading for tests, context caching, Spring-managed fixture injection, transactional test support, `MockMvc`, `WebTestClient` test bindings, `MockRestServiceServer`, and related lifecycle extensions.

Neighboring modules retain their own semantics:

- JUnit/TestNG lifecycle, assertions, parameterized tests, and engine mechanics belong to general Java testing.
- Spring Boot owns `@SpringBootTest`, test slices, Boot test auto-configuration, and Boot-specific context customization.
- `spring-framework/web` and `reactive` own production MVC/WebFlux behavior; this module only explains how testing infrastructure exercises it.
- `transaction-management` owns transaction propagation, isolation, and production transaction semantics; this module owns **test-managed transaction behavior**.

When a section needs one of those concepts, it explains only enough to make the testing mechanism understandable and then hands ownership back to the neighboring module.

</details>

- [Back to top](#back-to-top)
