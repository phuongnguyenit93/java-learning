<a id="back-to-top"></a>

# Context Caching, Isolation, and Parallelism

## Menu
- [Why Spring caches test ApplicationContexts](#context-cache-purpose)
- [How the context cache key is formed](#context-cache-key)
- [Static cache and JVM process boundary](#context-cache-process-boundary)
- [Cache size, eviction, and reuse cost](#cache-size-and-eviction)
- [DirtiesContext and deliberate invalidation](#dirties-context)
- [Context failure threshold in Spring Framework 6.1](#context-failure-threshold)
- [Parallel test execution and context-safety constraints](#parallel-test-execution)

## <a id="context-cache-purpose">Why Spring caches test ApplicationContexts</a>

<details>
<summary>Click for details</summary>

Loading an `ApplicationContext` can be one of the most expensive parts of a Spring integration test. Bean definitions must be discovered, configuration must be processed, singleton beans must be created, and infrastructure such as data sources or message clients may be initialized. Rebuilding the same context for every test method would make a large suite much slower without increasing the evidence produced by the test.

The TestContext framework therefore keeps successfully loaded contexts in a cache and reuses them when another test asks for the same effective test configuration. The reuse is primarily across test classes that share a cache key; a new test instance does not imply a new Spring context. This is why a well-structured suite can contain many integration tests while paying the context startup cost only a limited number of times.

Caching also creates a responsibility: singleton beans and other context-owned state can outlive one test method. A test that mutates such state must either restore it, design the bean to be safely reusable, or deliberately invalidate the context. Context caching improves performance; it is not an isolation mechanism by itself.

### References

- [Spring Framework 6.1 Reference — Context Caching](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="context-cache-key">How the context cache key is formed</a>

<details>
<summary>Click for details</summary>

Spring reuses a context only when the merged test configuration produces the same cache key. In Spring Framework 6.1, the key can include configuration locations and classes, context initializer classes, context customizers, the `ContextLoader`, a parent context, active profiles, test property-source descriptors and inline properties, and the web resource base path from `@WebAppConfiguration`.

Some configuration enters the key indirectly. `@DynamicPropertySource` contributes through a context customizer whose identity reflects the annotated registration methods, not each runtime value later returned by their suppliers. Different dynamic-property methods or other different customizers can therefore produce different cache keys, while subclasses that inherit the same registration may still reuse one context even if a supplier later returns a different value. If that value requires a different context, use `@DirtiesContext` or otherwise make the configuration identity differ.

This makes configuration consistency a performance concern. Repeating the same configuration in equivalent forms is less useful than sharing a stable test configuration or composed annotation. Before increasing cache size, inspect whether the suite is accidentally producing many distinct keys through different profiles, properties, customizers, or parent hierarchies.

### References

- [Spring Framework 6.1 Reference — Context Caching](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="context-cache-process-boundary">Static cache and JVM process boundary</a>

<details>
<summary>Click for details</summary>

The TestContext context cache is static inside the JVM process running the tests. That choice lets separate test classes and separate test instances in the same process see the same cache. It does not make the cache global to a build machine, Gradle invocation, or CI job.

If the build tool forks tests into another JVM, that process starts with its own empty cache. A suite that creates one JVM per test class therefore cannot benefit from Spring's cross-class context reuse. The same applies when separate test tasks run in separate worker processes: equal Spring configuration does not cross the process boundary.

When diagnosing unexpectedly slow context startup, first establish the actual process model. A high context-load count can be caused by many unique cache keys, by deliberate invalidation, or simply by JVM forking. Those causes need different fixes.

</details>

- [Back to top](#back-to-top)

---

## <a id="cache-size-and-eviction">Cache size, eviction, and reuse cost</a>

<details>
<summary>Click for details</summary>

The default TestContext cache has a maximum size of 32 contexts. When the cache is full, Spring uses a least-recently-used eviction policy. An evicted `ApplicationContext` is removed and closed, so a later test needing that same key must load it again.

The maximum can be changed with the `spring.test.context.cache.maxSize` system property or the equivalent Spring properties mechanism. A larger number is useful only when the suite legitimately needs more simultaneously reusable context configurations and the machine has enough memory. Increasing it to hide accidental configuration fragmentation trades memory for startup time without addressing the underlying cause.

For performance work, observe cache statistics instead of guessing. DEBUG logging for `org.springframework.test.context.cache` exposes cache activity and statistics. A suite with a high miss count should be reviewed for duplicated configuration, unnecessary profile/property variation, frequent `@DirtiesContext`, or process forking.

</details>

- [Back to top](#back-to-top)

---

## <a id="dirties-context">DirtiesContext and deliberate invalidation</a>

<details>
<summary>Click for details</summary>

`@DirtiesContext` tells the TestContext framework that a context is no longer safe to reuse. Depending on where the annotation is declared and its configured class or method mode, Spring removes the matching context before or after the selected test boundary. The next test that needs the same configuration receives a freshly loaded context.

Use this when the test changes context-owned state in a way that cannot be reliably restored, changes infrastructure represented by the context, or intentionally verifies behavior that leaves the context unusable. It is stronger than clearing one mock or resetting one bean: invalidation discards the complete cached context and therefore carries a startup cost.

Context hierarchies deserve extra care. `@DirtiesContext` can invalidate a broader hierarchy or only the current level through its hierarchy mode. Choose the narrowest behavior that matches the state actually made dirty. If a test can clean up a local fixture directly, that is normally cheaper and clearer than rebuilding the whole Spring context.

### References

- [Spring Framework 6.1 Reference — `@DirtiesContext`](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="context-failure-threshold">Context failure threshold in Spring Framework 6.1</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 introduced a context failure threshold so the suite does not repeatedly attempt to load a context that has already failed for the same cache key. The default threshold is `1`: after the first failed load attempt, another attempt for that key is preemptively rejected with an `IllegalStateException` instead of repeating the expensive failure.

The threshold is configured with the positive integer property `spring.test.context.failure.threshold`. A very large value can effectively disable the fail-fast behavior when repeated attempts are intentionally useful, but that should be a deliberate diagnostic choice rather than a routine suite setting.

The key detail is that the threshold follows the context cache key. A failure for one configuration does not mean every Spring test context is blocked. When several tests fail immediately with the threshold message, investigate the first failure for that key; later failures are usually consequences of the original context-loading problem.

### References

- [Spring Framework 6.1 Reference — Context Failure Threshold](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)
- [Spring Framework 6.1.14 API — ContextCache](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/cache/ContextCache.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="parallel-test-execution">Parallel test execution and context-safety constraints</a>

<details>
<summary>Click for details</summary>

The TestContext framework can participate in parallel test execution within one JVM, but the test engine or build tool controls whether tests actually run concurrently. Spring's support means its default `TestContext` can be copied for concurrent execution and the context cache can be accessed safely; it does not make application beans, mutable fixtures, databases, files, or other shared systems thread-safe.

Parallel execution is a poor fit for tests that use `@DirtiesContext`, depend on a required test-method order, or mutate the same external state. One thread can remove or evict a cached context while another test still expects to use it, leading to failures that appear unrelated to the test currently running. Heavy cache churn can create a similar problem when the maximum cache size is too small for the concurrent workload.

Review thread-bound infrastructure as well. Test-managed transactions are associated with the thread that Spring prepares for the test. A testing feature that executes the test body on another thread, such as a preemptive timeout, can bypass that transaction. Parallelism should therefore be enabled only after each test's Spring context, transaction boundary, and external resources are independently safe.

If a third-party extension supplies a custom `TestContext` implementation, verify that it supports the copy-constructor contract required for parallel TestContext execution.

### References

- [Spring Framework 6.1 Reference — Parallel Test Execution](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Back to top](#back-to-top)
