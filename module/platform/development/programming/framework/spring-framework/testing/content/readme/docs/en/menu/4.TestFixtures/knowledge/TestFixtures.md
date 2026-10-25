<a id="back-to-top"></a>

# Test Fixtures and Web Context

## Menu
- [Dependency injection into test fixtures](#fixture-dependency-injection)
- [Test instance lifecycle and constructor injection](#test-instance-and-constructor-injection)
- [Accessing the ApplicationContext in tests](#application-context-access)
- [WebApplicationContext test fixtures](#web-application-context-fixtures)
- [Testing request- and session-scoped beans](#request-and-session-scoped-fixtures)

## <a id="fixture-dependency-injection">Dependency injection into test fixtures</a>

<details>
<summary>Click for details</summary>

The TestContext Framework can inject dependencies into a test instance after the test engine creates it. The default `DependencyInjectionTestExecutionListener` performs this work.

That means fields and setter methods can use familiar Spring injection annotations:

```java
@Autowired
PricingService pricingService;
```

The test class itself is not normally a Spring bean. Spring is enriching an already-created test object from the test `ApplicationContext`.

Injection is useful when the test needs the exact bean graph that Spring assembled. If the test only needs a collaborator interface and does not care how Spring resolves it, direct construction is still the simpler test.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-instance-and-constructor-injection">Test instance lifecycle and constructor injection</a>

<details>
<summary>Click for details</summary>

Constructor injection in tests depends on the integration between the test engine and Spring. With JUnit Jupiter plus `SpringExtension`, Spring treats the whole test constructor as autowirable when:

- the constructor itself is annotated with `@Autowired`, `@jakarta.inject.Inject`, or `@javax.inject.Inject`;
- `@TestConstructor(autowireMode = ALL)` applies; or
- the global `spring.test.constructor.autowire.mode=all` setting is used.

When Spring owns the whole constructor, it assumes responsibility for resolving every constructor parameter, so other Jupiter `ParameterResolver` implementations do not get to resolve those parameters independently.

Whole-constructor autowiring is not the only constructor-injection path. If the constructor itself is not fully autowirable, `SpringExtension` can still resolve individual parameters that Spring recognizes, such as `ApplicationContext`/`ApplicationEvents` parameters or parameters annotated or meta-annotated for dependency injection (for example with `@Autowired`, `@Qualifier`, or `@Value`). Other registered parameter resolvers can still handle constructor parameters that Spring does not claim.

Do not use constructor injection with JUnit's `@TestInstance(PER_CLASS)` when `@DirtiesContext` is configured to close the test `ApplicationContext` before or after test methods. With `PER_CLASS`, JUnit keeps the same test instance, and its constructor is not invoked again after Spring closes and replaces the context, so constructor-injected references would still point to beans from the closed context. Use field or setter injection in that situation so Spring can reinject dependencies from the current context.

### References

- [Spring Framework 6.1.14 API — TestConstructor](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/TestConstructor.html)
- [Spring Framework 6.1.14 API — SpringExtension](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/junit/jupiter/SpringExtension.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="application-context-access">Accessing the ApplicationContext in tests</a>

<details>
<summary>Click for details</summary>

Tests can inject the `ApplicationContext` itself:

```java
@Autowired
ApplicationContext context;
```

This is appropriate when the behavior being tested is container-level: bean presence, environment state, event publication, bean metadata, or configuration composition.

Avoid using `getBean()` merely as a service locator for every dependency. Normal fixture injection communicates intent better and keeps the test focused.

The context reference generally points to the same cached instance used by other tests with an equivalent merged configuration. Therefore mutating singleton state in one test can leak into another even when the test classes are different.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-application-context-fixtures">WebApplicationContext test fixtures</a>

<details>
<summary>Click for details</summary>

For a test configured with `@WebAppConfiguration`, Spring can provide a `WebApplicationContext` backed by servlet test objects instead of a real servlet container.

The `ServletTestExecutionListener` prepares servlet-specific thread-local state when required, including mock request infrastructure used by web-scoped components.

```java
@Autowired
WebApplicationContext webContext;
```

This fixture is useful for `MockMvcBuilders.webAppContextSetup(webContext)` and for tests that need to exercise beans whose behavior depends on web scopes or servlet resources.

It proves Spring web-context behavior, not networking. No real HTTP server or socket exists unless the test explicitly starts one outside this infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="request-and-session-scoped-fixtures">Testing request- and session-scoped beans</a>

<details>
<summary>Click for details</summary>

Request- and session-scoped beans need an active request context. `ServletTestExecutionListener` prepares servlet test state such as `MockHttpServletRequest`, `MockHttpServletResponse`, and `ServletWebRequest`, then binds the request context to the current thread so those scopes can be exercised without a container.

A session is created lazily from the mock request when the test asks for one. A fixture can therefore prepare request/session state like this:

```java
ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
MockHttpServletRequest request =
        (MockHttpServletRequest) attributes.getRequest();
request.setParameter("locale", "vi");
request.getSession().setAttribute("cartId", "C-42");
```

The important behavior is that the session comes from the request rather than being independently created by the listener. When a scoped proxy is injected, method calls resolve the target from the currently bound request/session state.

This is still an integration-style fixture. If the real requirement is only business logic inside the scoped bean, extract that logic into ordinary collaborators and test them without servlet scope infrastructure.

</details>

- [Back to top](#back-to-top)
