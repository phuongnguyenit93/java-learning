<a id="back-to-top"></a>

# Test Properties and `@TestConfiguration`

## Menu
- [Why Customize a Boot Test Context for One Scenario?](#test-context-customization-purpose)
- [How Do Boot Test Annotation Properties Override Configuration Locally?](#springboottest-properties)
- [What Problem Does `@TestConfiguration` Solve?](#testconfiguration-purpose)
- [How Is Test-Only Configuration Added Without Replacing the Primary Application Configuration?](#testconfiguration-import)
- [How Can Property and Configuration Differences Fragment Context Reuse?](#test-property-cache-impact)
- [Where Do Test-Specific Overrides Hand Off to Externalized Configuration and Spring TestContext?](#test-configuration-ownership-boundary)

## <a id="test-context-customization-purpose">Why Customize a Boot Test Context for One Scenario?</a>

<details>
<summary>Click for details</summary>
Tests sometimes need a controlled variation of the application: a feature flag enabled, a timeout shortened, one external endpoint replaced, or a test-only bean added. Boot provides local customization mechanisms so these changes can remain test-scoped instead of leaking into normal application configuration.

Use the smallest customization that expresses the scenario. Every additional property or configuration class can also affect context reuse, so test customization is part of suite design rather than free setup.

</details>

- [Back to top](#back-to-top)

---

## <a id="springboottest-properties">How Do Boot Test Annotation Properties Override Configuration Locally?</a>

<details>
<summary>Click for details</summary>
Boot test annotations such as `@SpringBootTest` accept a `properties` attribute for inline test-specific properties. Those values are added to the test environment for that test context and can override ordinary application configuration for the scenario.

```java
@SpringBootTest(properties = "feature.checkout.enabled=true")
class CheckoutIntegrationTest {
}
```

This is convenient for small local overrides. The broader precedence and binding model remains owned by externalized configuration and Spring TestContext property-source support.

</details>

- [Back to top](#back-to-top)

---

## <a id="testconfiguration-purpose">What Problem Does `@TestConfiguration` Solve?</a>

<details>
<summary>Click for details</summary>
`@TestConfiguration` marks configuration intended specifically for tests. Unlike a nested plain `@Configuration` used as the primary test configuration, a nested `@TestConfiguration` is added alongside the application's normal primary configuration.

This is useful for test-only beans, alternative infrastructure adapters, or reusable support that should never be discovered as ordinary production configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="testconfiguration-import">How Is Test-Only Configuration Added Without Replacing the Primary Application Configuration?</a>

<details>
<summary>Click for details</summary>
A nested `@TestConfiguration` is automatically combined with the primary Boot configuration for that test arrangement. A top-level reusable `@TestConfiguration` can be imported explicitly with `@Import` when several tests need the same support.

The important distinction is additive intent: test configuration supplements the production application model rather than silently becoming the application's main configuration.

### References

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="test-property-cache-impact">How Can Property and Configuration Differences Fragment Context Reuse?</a>

<details>
<summary>Click for details</summary>
Spring TestContext reuses contexts only when the effective test configuration matches. Different inline properties, profiles, imported configuration, mocks, and other context customizers can contribute to different cache keys.

Therefore, many tiny one-off configuration variants can make a suite repeatedly start similar Boot contexts. Prefer shared test configurations and stable property sets when they express the same scenario boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-configuration-ownership-boundary">Where Do Test-Specific Overrides Hand Off to Externalized Configuration and Spring TestContext?</a>

<details>
<summary>Click for details</summary>
Boot owns convenience attributes and `@TestConfiguration` integration used by Boot test annotations. Externalized configuration owns normal property sources, precedence, profiles, binding, and `@ConfigurationProperties` semantics.

Spring TestContext owns generic test property sources, dynamic property registration, context customization, and context caching. This module explains how Boot uses those capabilities without redefining their underlying mechanics.

</details>

- [Back to top](#back-to-top)
