# Spring Boot Testing

This module explains how Spring Boot builds application-aware test contexts and helps you choose the smallest useful level of integration. It focuses on Boot-specific test bootstrap, full `@SpringBootTest` contexts, web environments, focused test slices, test auto-configuration, test-only context customization, and Testcontainers service connections.

## What You Will Learn

You will learn how Boot discovers primary application configuration, when a full context is necessary, how `WebEnvironment` changes server fidelity and transaction boundaries, how web and data slices limit a context, how `@AutoConfigure...` support customizes test infrastructure, how properties, `@TestConfiguration`, `@MockBean`, and `@SpyBean` affect a test context and its reuse cost, and how `@ServiceConnection` turns supported containers into `ConnectionDetails` for real-service integration tests.

## Prerequisites

You should already understand Spring Boot fundamentals, auto-configuration, externalized configuration, web runtime basics, and ordinary Spring application contexts. This module assumes familiarity with basic testing vocabulary but does not teach JUnit, Mockito, AssertJ, generic Spring TestContext lifecycle/transactions, or Testcontainers container mechanics.

## Learning Flow

1. Establish what Boot adds to application-context testing and how it discovers the application's primary configuration.
2. Use `@SpringBootTest` for full contexts, then choose the correct `WebEnvironment` and understand the real-server transaction boundary.
3. Prefer the smallest useful Boot slice and learn focused web and data slice models.
4. Understand the test auto-configuration behind slices and customize it deliberately with `@AutoConfigure...`, exclusions, and imports.
5. Customize test contexts with local properties, `@TestConfiguration`, mocks, and spies while accounting for context-cache fragmentation.
6. Connect supported Testcontainers to Boot through `@ServiceConnection` and `ConnectionDetails`, using `@DynamicPropertySource` only when a service connection is not suitable.
7. Synthesize a testing strategy that balances context size, production fidelity, real-server and real-service coverage, and suite startup cost.

## Module Boundary

This module owns Spring Boot's test bootstrap, `@SpringBootTest`, Boot test slices and their auto-configuration, Boot-specific test context customization, and Testcontainers service-connection integration. Generic Spring TestContext lifecycle, transaction and cache mechanics belong to Spring Framework testing; JUnit, Mockito, AssertJ, and other testing-library fundamentals belong to their own curricula; generic Testcontainers lifecycle, images, networks, and container APIs remain outside Boot ownership.
