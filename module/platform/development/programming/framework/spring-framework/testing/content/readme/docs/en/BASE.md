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
