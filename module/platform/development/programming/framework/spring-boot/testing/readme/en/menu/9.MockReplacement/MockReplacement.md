<a id="back-to-top"></a>

# Replacing and Spying on Beans in Boot Tests

## Menu
- [When Should `@MockBean` Add or Replace a Bean in the Test Context?](#mockbean-purpose)
- [When Should `@SpyBean` Wrap an Existing Bean?](#spybean-purpose)
- [How Does Bean Replacement Change the Boot Test Context?](#mock-replacement-context-boundary)
- [Why Can `@MockBean` Not Reconfigure Behavior Needed During Context Refresh?](#mock-refresh-time-limit)
- [How Do Mock and Spy Definitions Affect the Test Context Cache Key?](#mock-spy-cache-impact)
- [Where Does Boot Bean Replacement End and Mockito Behavior Begin?](#mockito-testing-boundary)

## <a id="mockbean-purpose">When Should `@MockBean` Add or Replace a Bean in the Test Context?</a>

<details>
<summary>Click for details</summary>
`@MockBean` integrates Mockito mocks with a Spring Boot test `ApplicationContext`. It can add a new bean when no matching bean exists or replace a single existing bean definition so Boot-managed components receive the mock through normal dependency injection.

Use it when the test boundary includes the Spring context but one collaborator should be controlled rather than started for real. A web slice commonly replaces service collaborators this way. Plain unit tests that do not need a Spring context should use Mockito directly instead.

### References

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="spybean-purpose">When Should `@SpyBean` Wrap an Existing Bean?</a>

<details>
<summary>Click for details</summary>
`@SpyBean` keeps the real bean in the Boot context but wraps it with a Mockito spy. This is useful when most production behavior should run normally while the test observes interactions or overrides a small part of the behavior.

Because the real bean and its dependencies still exist, a spy is not a cheaper substitute for a mock. It is an integration-oriented choice and may interact with Spring proxies or caching infrastructure; Mockito stubbing details and proxy unwrapping belong to their respective owners.

</details>

- [Back to top](#back-to-top)

---

## <a id="mock-replacement-context-boundary">How Does Bean Replacement Change the Boot Test Context?</a>

<details>
<summary>Click for details</summary>
Mock and spy definitions are context customizers. They alter the effective bean graph that Spring creates for the test and therefore change the identity of the cached test context.

This is valuable because the replacement participates in ordinary autowiring, but it also means two otherwise identical tests with different mock/spy definitions may not reuse the same context. Treat dependency replacement as part of the test-context design rather than as an invisible local variable.

</details>

- [Back to top](#back-to-top)

---

## <a id="mock-refresh-time-limit">Why Can `@MockBean` Not Reconfigure Behavior Needed During Context Refresh?</a>

<details>
<summary>Click for details</summary>
`@MockBean` creates the mock before context refresh, but test-method stubbing normally happens only after the context has already been refreshed. If another bean needs a specific mocked return value **during** its own initialization, configuring that behavior in the test method is too late.

Boot's documentation recommends defining and configuring such a mock through a `@Bean` method in test configuration. That lets the behavior exist before dependent beans initialize.

</details>

- [Back to top](#back-to-top)

---

## <a id="mock-spy-cache-impact">How Do Mock and Spy Definitions Affect the Test Context Cache Key?</a>

<details>
<summary>Click for details</summary>
Spring's context cache includes the effective context customizers, including Boot mock and spy definitions. Different replacement sets can therefore produce different cache keys and force additional context startups.

A suite with many tests that each declare slightly different mocks can become unexpectedly slow even when every individual test is small. Reuse stable context shapes where practical, and prefer plain unit tests when Spring integration is not part of the behavior being proven.

</details>

- [Back to top](#back-to-top)

---

## <a id="mockito-testing-boundary">Where Does Boot Bean Replacement End and Mockito Behavior Begin?</a>

<details>
<summary>Click for details</summary>
Boot owns the integration that registers Mockito-created mocks or spies as beans, injects them into the test, and resets mocks according to Boot's test support. Mockito owns how mocks are created, stubbed, verified, matched, and spied.

Questions such as `when` versus `doReturn`, argument matchers, strictness, verification modes, or spy semantics belong to Mockito learning. Boot testing only explains how those test doubles enter a Spring application context.

</details>

- [Back to top](#back-to-top)
