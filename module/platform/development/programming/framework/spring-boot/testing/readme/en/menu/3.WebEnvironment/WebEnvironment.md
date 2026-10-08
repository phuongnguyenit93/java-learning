<a id="back-to-top"></a>

# `WebEnvironment` and Real-Server Test Boundaries

## Menu
- [What Does `WebEnvironment` Control?](#web-environment-model)
- [What Does `WebEnvironment.MOCK` Provide Without Starting a Server?](#web-environment-mock)
- [How Do `RANDOM_PORT` and `DEFINED_PORT` Start a Real Embedded Server?](#web-environment-real-server)
- [When Does `WebEnvironment.NONE` Fit?](#web-environment-none)
- [How Do Boot-Provided Test Clients Fit a Running-Server Test?](#real-server-test-clients)
- [Why Does a Real-Server Test Change Transaction Rollback Expectations?](#real-server-transaction-boundary)
- [Where Does Testing Ownership Hand Off to Web Runtime and Spring Web Testing?](#web-runtime-testing-handoff)

## <a id="web-environment-model">What Does `WebEnvironment` Control?</a>

<details>
<summary>Click for details</summary>
`SpringBootTest.WebEnvironment` controls the kind of web context used by a full Boot test and whether an embedded server is started. The four values are `MOCK`, `RANDOM_PORT`, `DEFINED_PORT`, and `NONE`.

This is a test bootstrap choice, not a web-framework API. It decides how Boot establishes the environment in which MVC or WebFlux infrastructure will be tested.

### References

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="web-environment-mock">What Does `WebEnvironment.MOCK` Provide Without Starting a Server?</a>

<details>
<summary>Click for details</summary>
`MOCK` is the default. When a supported web stack is present, Boot loads a web `ApplicationContext` with a mock web environment but does not start the embedded server. If the classpath has no web environment, Boot falls back to a normal non-web context.

For MVC applications, pair this mode with `MockMvc` for mock request processing. For WebFlux applications, Boot can auto-configure `WebTestClient` for the mocked reactive web application. In Spring Boot 3.3, mocked `WebTestClient` support is a WebFlux path; do not present it as the MVC equivalent of `MockMvc`.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-environment-real-server">How Do `RANDOM_PORT` and `DEFINED_PORT` Start a Real Embedded Server?</a>

<details>
<summary>Click for details</summary>
Both `RANDOM_PORT` and `DEFINED_PORT` load a `WebServerApplicationContext` and start the embedded web server. `RANDOM_PORT` asks the server to listen on an available port; `DEFINED_PORT` uses the configured application port or the normal default.

`RANDOM_PORT` is usually safer for automated suites because parallel runs do not compete for one fixed port. The actual port can be injected with `@LocalServerPort` when a custom client needs it.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-environment-none">When Does `WebEnvironment.NONE` Fit?</a>

<details>
<summary>Click for details</summary>
`NONE` still boots the application through `SpringApplication` but configures no web environment. It is useful when the test needs full Boot configuration and auto-configuration while deliberately excluding Servlet or reactive web runtime concerns.

For example, a command-line application, scheduler, batch-oriented component, or configuration integration test may want Boot startup fidelity without allocating mock web infrastructure or a server.

</details>

- [Back to top](#back-to-top)

---

## <a id="real-server-test-clients">How Do Boot-Provided Test Clients Fit a Running-Server Test?</a>

<details>
<summary>Click for details</summary>
For a real-server test, Boot can provide a `WebTestClient` that resolves relative URLs against the running server. If WebFlux is not available or should not be added for test-client use, Boot also provides `TestRestTemplate` for REST-style calls.

These clients are conveniences around the chosen web environment. They do not decide whether the server exists; `WebEnvironment` does. Client assertion and request APIs belong to their respective Spring testing facilities.

</details>

- [Back to top](#back-to-top)

---

## <a id="real-server-transaction-boundary">Why Does a Real-Server Test Change Transaction Rollback Expectations?</a>

<details>
<summary>Click for details</summary>
With `RANDOM_PORT` or `DEFINED_PORT`, the test client and the server process a request on different threads. A test-managed `@Transactional` transaction therefore does not automatically contain the transaction started by application code on the server side.

The transaction around the test method can still roll back its own work, but changes committed by the server may remain. The exact semantics of test-managed transactions belong to Spring TestContext; the Boot-specific lesson is that starting a real server crosses that transaction boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-runtime-testing-handoff">Where Does Testing Ownership Hand Off to Web Runtime and Spring Web Testing?</a>

<details>
<summary>Click for details</summary>
Boot testing owns the choice of `WebEnvironment`, Boot-provided running-server clients, and how those facilities integrate with a Boot test context. Production server selection, ports, TLS, forwarded headers, and other server runtime behavior belong to the Spring Boot web-runtime module.

`MockMvc`, Spring MVC test APIs, `WebTestClient` request/assertion mechanics, and generic web-test framework behavior belong to Spring Framework testing. This module uses those tools without redefining them.

</details>

- [Back to top](#back-to-top)
