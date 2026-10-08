<a id="back-to-top"></a>

# Spring Boot Test Bootstrap and Configuration Discovery

## Menu
- [What Does Spring Boot Add to Application-Context Testing?](#boot-testing-role)
- [Why Is Boot-Aware Test Bootstrap Needed?](#boot-testing-problem)
- [How Do `spring-boot-test`, `spring-boot-test-autoconfigure`, and `spring-boot-starter-test` Relate?](#boot-test-support-modules)
- [How Does `@SpringBootTest` Bootstrap Through `SpringApplication`?](#springapplication-test-bootstrap)
- [How Does Boot Find the Primary `@SpringBootConfiguration`?](#primary-test-configuration-discovery)
- [Where Does Boot Testing Hand Off to Spring TestContext and Test Libraries?](#testing-ownership-boundary)
- [Which Boot Testing Choice Comes Next?](#testing-learning-path)

## <a id="boot-testing-role">What Does Spring Boot Add to Application-Context Testing?</a>

<details>
<summary>Click for details</summary>
Spring Framework already provides the TestContext Framework for loading and reusing application contexts in tests. Spring Boot adds a Boot-aware layer on top of that foundation: it can start the test context through `SpringApplication`, discover the application's primary Boot configuration, apply Boot external configuration and auto-configuration, and expose focused test annotations for common application slices.

The key question for this module is therefore not “how does testing work in Java?” but “which Boot facilities should participate when a test needs application infrastructure?”. JUnit execution, assertion libraries, Mockito stubbing semantics, and the generic TestContext lifecycle remain separate concerns.

### References

- [Spring Boot 3.3 — Testing](https://docs.spring.io/spring-boot/3.3/reference/testing/index.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-testing-problem">Why Is Boot-Aware Test Bootstrap Needed?</a>

<details>
<summary>Click for details</summary>
A production Boot application normally depends on more than plain bean registration. It may rely on externalized configuration, auto-configuration, environment detection, configuration-properties binding, and conditional infrastructure. Loading a context with a generic Spring test annotation can skip Boot behavior that the production application depends on.

Boot-aware test bootstrap exists so a test can reproduce the relevant Boot startup model without manually rebuilding it. The appropriate level still depends on the behavior under test: some tests need the full Boot context, while others should intentionally use a smaller slice.

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-test-support-modules">How Do `spring-boot-test`, `spring-boot-test-autoconfigure`, and `spring-boot-starter-test` Relate?</a>

<details>
<summary>Click for details</summary>
`spring-boot-test` contains core Boot testing support such as `@SpringBootTest` and Boot-specific test utilities. `spring-boot-test-autoconfigure` contains the slice annotations and test auto-configuration used for focused contexts.

Most projects depend on `spring-boot-starter-test` instead of selecting these modules individually. The starter brings both Boot test modules plus commonly used testing libraries such as JUnit Jupiter, AssertJ, Hamcrest, and Mockito. Those libraries remain independently owned even though the starter makes them convenient to consume together.

</details>

- [Back to top](#back-to-top)

---

## <a id="springapplication-test-bootstrap">How Does `@SpringBootTest` Bootstrap Through `SpringApplication`?</a>

<details>
<summary>Click for details</summary>
`@SpringBootTest` creates the test `ApplicationContext` through `SpringApplication` rather than treating it as a plain Spring context. That allows Boot features such as external properties, logging configuration, environment processing, and auto-configuration to participate in the test startup path.

This gives full-context tests high production fidelity at the Boot integration layer. It also means failures during test startup should be reasoned about like Boot startup failures: configuration discovery, conditions, properties, and infrastructure can all matter before the first test method executes.

### References

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="primary-test-configuration-discovery">How Does Boot Find the Primary `@SpringBootConfiguration`?</a>

<details>
<summary>Click for details</summary>
When a Boot test annotation does not explicitly specify configuration classes, Boot searches upward from the package containing the test until it finds a class annotated with `@SpringBootApplication` or `@SpringBootConfiguration`. In a conventional package layout, this usually finds the application's main configuration automatically.

Package placement therefore affects test bootstrap. A test placed outside the application's package hierarchy may fail to locate the primary configuration unless the configuration class is supplied explicitly.

</details>

- [Back to top](#back-to-top)

---

## <a id="testing-ownership-boundary">Where Does Boot Testing Hand Off to Spring TestContext and Test Libraries?</a>

<details>
<summary>Click for details</summary>
Boot testing owns Boot-specific context bootstrap, slice selection, test auto-configuration, Boot test annotations, mock/spy bean replacement integration, and service-connection support. It relies on Spring TestContext underneath for context lifecycle, caching, test-managed transactions, property-source support, and test execution integration.

JUnit owns test discovery and execution semantics. Mockito owns mock behavior and verification. AssertJ/Hamcrest own assertion APIs. Testcontainers owns container definitions and its JUnit-managed container lifecycle, along with Docker interaction, images, networking, wait strategies, and other generic container behavior. When a container is declared as a Spring bean, its lifecycle follows the Spring application context instead. Keeping these boundaries explicit prevents Boot convenience annotations from being mistaken for the implementation of the underlying testing tools.

</details>

- [Back to top](#back-to-top)

---

## <a id="testing-learning-path">Which Boot Testing Choice Comes Next?</a>

<details>
<summary>Click for details</summary>
Start by deciding the **boundary that the test must prove**. Use a full `@SpringBootTest` context when Boot-wide integration matters. Choose the correct `WebEnvironment` when HTTP-server fidelity matters. Prefer a focused slice when only a web or data layer must be tested.

After choosing context scope, customize only what the scenario needs: selected test auto-configuration, local properties, test-only configuration, mock/spy replacement, or service connections to real dependencies. The final chapter combines those choices into a deliberate testing strategy.

</details>

- [Back to top](#back-to-top)
