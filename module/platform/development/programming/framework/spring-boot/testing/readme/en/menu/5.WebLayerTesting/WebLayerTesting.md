<a id="back-to-top"></a>

# Focused Web Testing with `@WebMvcTest` and `@WebFluxTest`

## Menu
- [What Does `@WebMvcTest` Select for an MVC Test?](#webmvc-test-purpose)
- [How Are Controller Collaborators Supplied to an `@WebMvcTest`?](#webmvc-test-collaborators)
- [What Does `@WebFluxTest` Select for a Reactive Web Test?](#webflux-test-purpose)
- [Which Test Clients Are Auto-Configured by Web Slices?](#web-slice-auto-configured-clients)
- [When Is a Web Slice Enough and When Is a Real Server Necessary?](#mock-web-vs-real-server)
- [Where Does Boot Web-Slice Configuration Hand Off to MVC or WebFlux Testing Mechanics?](#web-testing-framework-boundary)

## <a id="webmvc-test-purpose">What Does `@WebMvcTest` Select for an MVC Test?</a>

<details>
<summary>Click for details</summary>
`@WebMvcTest` creates a focused Spring MVC test slice. It selects MVC-oriented components such as controllers and related web infrastructure while excluding most application services, repositories, and unrelated auto-configuration.

This makes it suitable for verifying request mapping, validation integration, serialization, controller advice, filters that belong in the selected web slice, and MVC configuration at the Boot integration layer without starting the whole application.

### References

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="webmvc-test-collaborators">How Are Controller Collaborators Supplied to an `@WebMvcTest`?</a>

<details>
<summary>Click for details</summary>
Because services and repositories are normally outside the slice, controller collaborators must be provided explicitly. A common Boot-specific option is `@MockBean`, which adds or replaces a bean in the test context so the controller can be exercised without loading the collaborator's production layer.

If the test keeps importing many production collaborators to make the slice work, reassess the boundary. The behavior may actually require a larger integration context rather than a heavily reconstructed web slice.

</details>

- [Back to top](#back-to-top)

---

## <a id="webflux-test-purpose">What Does `@WebFluxTest` Select for a Reactive Web Test?</a>

<details>
<summary>Click for details</summary>
`@WebFluxTest` is the focused reactive-web counterpart. It loads WebFlux-oriented infrastructure and selected reactive web components while excluding unrelated application layers.

Use it when the objective is routing/controller behavior, codecs, validation integration, exception handling, or reactive web configuration at the framework boundary. Reactive pipeline semantics and Reactor behavior themselves remain outside Boot testing ownership.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-slice-auto-configured-clients">Which Test Clients Are Auto-Configured by Web Slices?</a>

<details>
<summary>Click for details</summary>
`@WebMvcTest` auto-configures `MockMvc`, allowing MVC request processing to be exercised without starting an HTTP server. `@WebFluxTest` can auto-configure `WebTestClient` for the reactive web slice.

Boot is responsible for making these clients available in the selected test context. The detailed request builder, exchange, expectation, and assertion APIs belong to Spring's web testing support.

</details>

- [Back to top](#back-to-top)

---

## <a id="mock-web-vs-real-server">When Is a Web Slice Enough and When Is a Real Server Necessary?</a>

<details>
<summary>Click for details</summary>
A web slice is enough when the behavior can be proven entirely within the framework request-processing boundary: mappings, validation, serialization, advice, security integration configured for the slice, or controller collaboration.

Use a real-server `@SpringBootTest` when the assertion depends on embedded-server behavior, actual network boundaries, server configuration, real HTTP client/server interaction, or cross-layer application wiring that the slice deliberately excludes.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-testing-framework-boundary">Where Does Boot Web-Slice Configuration Hand Off to MVC or WebFlux Testing Mechanics?</a>

<details>
<summary>Click for details</summary>
Boot owns which components and auto-configurations enter `@WebMvcTest` or `@WebFluxTest`, plus the auto-configuration of their test clients. Spring Framework owns how `MockMvc` and `WebTestClient` execute requests and assert framework behavior.

The production MVC/WebFlux request pipeline belongs to the Spring Framework web modules. Boot testing only provides the focused environment in which that pipeline is exercised.

</details>

- [Back to top](#back-to-top)
