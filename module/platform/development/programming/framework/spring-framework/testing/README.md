# 📂 README MODULE STRUCTURE (EN)

* **1.Introduction**
    * [SpringTesting](readme/en/menu/1.Introduction/SpringTesting.md)
* **2.TestContextFramework**
    * [TestContextFramework](readme/en/menu/2.TestContextFramework/TestContextFramework.md)
* **3.ContextConfiguration**
    * [ContextConfiguration](readme/en/menu/3.ContextConfiguration/ContextConfiguration.md)
* **4.TestFixtures**
    * [TestFixtures](readme/en/menu/4.TestFixtures/TestFixtures.md)
* **5.TestEnvironment**
    * [TestEnvironment](readme/en/menu/5.TestEnvironment/TestEnvironment.md)
* **6.ContextCaching**
    * [ContextCaching](readme/en/menu/6.ContextCaching/ContextCaching.md)
* **7.TransactionalDatabaseTesting**
    * [TransactionalDatabaseTesting](readme/en/menu/7.TransactionalDatabaseTesting/TransactionalDatabaseTesting.md)
* **8.MockMvcTesting**
    * [MockMvcTesting](readme/en/menu/8.MockMvcTesting/MockMvcTesting.md)
* **9.WebTestClientAndClientTesting**
    * [WebTestClientAndClientTesting](readme/en/menu/9.WebTestClientAndClientTesting/WebTestClientAndClientTesting.md)
* **10.TestLifecycle**
    * [TestLifecycle](readme/en/menu/10.TestLifecycle/TestLifecycle.md)

# Spring Framework Testing

This module explains how Spring Framework supports testing when plain object-level tests are no longer enough to prove application behavior.

The learning path starts with the boundary between ordinary unit tests and Spring-managed integration tests, then moves through the TestContext Framework, ApplicationContext configuration and fixture injection, test-specific profiles and properties, context caching and isolation, transactional/database testing, Servlet and reactive web testing, and finally lifecycle/extensibility concerns.

Recommended prerequisites:

- Java testing fundamentals and a general-purpose test framework such as JUnit;
- Spring IoC / ApplicationContext fundamentals from `spring-framework/core-container`;
- Spring MVC concepts before the deeper MockMvc sections;
- Spring WebFlux concepts before the deeper WebTestClient sections;
- Spring transaction fundamentals before analyzing transactional test behavior in depth.

The core learning flow is:

```text
testability and test scope
→ TestContext runtime model
→ context configuration and test fixtures
→ profiles and test properties
→ context caching / isolation / parallelism
→ transactional and database testing
→ MockMvc
→ WebTestClient and client-side HTTP testing
→ lifecycle, AOT support, and testing-strategy synthesis
```

This module owns Spring Framework testing infrastructure such as TestContext, MockMvc, WebTestClient, MockRestServiceServer, context caching, transactional test support, and related lifecycle hooks. It does not own JUnit fundamentals, Spring Boot test slices or `@SpringBootTest`, the production internals of Spring MVC/WebFlux, or the general semantics of Spring transaction management.

The goal is to choose the smallest test scope that proves the behavior under test and to understand exactly which Spring infrastructure is participating in that proof.
