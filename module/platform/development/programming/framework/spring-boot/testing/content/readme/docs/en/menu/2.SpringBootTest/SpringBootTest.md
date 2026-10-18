<a id="back-to-top"></a>

# Full Application Contexts with `@SpringBootTest`

## Menu
- [When Is a Full Boot Application Context the Right Test Boundary?](#full-context-purpose)
- [What Does `@SpringBootTest` Load into the Test Context?](#springboottest-context-model)
- [How Does Full-Context Testing Reuse Boot's Primary Configuration Discovery?](#full-context-configuration-discovery)
- [What Fidelity and Startup Cost Come with a Full Context?](#full-context-fidelity-cost)
- [When Should a Focused Slice Replace `@SpringBootTest`?](#full-context-vs-slice)
- [How Does `WebEnvironment` Change the Full-Context Model?](#web-environment-handoff)

## <a id="full-context-purpose">When Is a Full Boot Application Context the Right Test Boundary?</a>

<details>
<summary>Click for details</summary>
Use a full Boot context when the behavior being verified depends on several application layers or on Boot's production-style configuration working together. Typical examples include configuration binding plus service wiring, cross-layer integration, security or messaging infrastructure, and startup conditions that a focused slice would intentionally omit.

Full-context testing should be a deliberate fidelity choice. It is more expensive to start and usually exposes more infrastructure than a focused test needs, so it should prove behavior that smaller tests cannot establish reliably.

</details>

- [Back to top](#back-to-top)

---

## <a id="springboottest-context-model">What Does `@SpringBootTest` Load into the Test Context?</a>

<details>
<summary>Click for details</summary>
By default, `@SpringBootTest` looks for the application's primary Boot configuration and asks `SpringApplication` to build the context. Application configuration, component scanning, auto-configuration, externalized properties, and other Boot startup facilities can therefore participate.

The annotation does not necessarily start a network server. Its default `WebEnvironment.MOCK` uses a mock web environment when web infrastructure is available. Server startup is controlled separately by the `webEnvironment` attribute.

### References

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="full-context-configuration-discovery">How Does Full-Context Testing Reuse Boot's Primary Configuration Discovery?</a>

<details>
<summary>Click for details</summary>
Full-context tests normally reuse the same primary `@SpringBootApplication` / `@SpringBootConfiguration` entry point that Boot discovers for the application. This reduces duplication between production and test wiring and makes auto-configuration conditions evaluate against a realistic configuration model.

If a test truly needs a different top-level configuration, it can supply explicit classes. Use that choice carefully: replacing the primary configuration changes the meaning of the test and can make it less representative of the application startup path.

</details>

- [Back to top](#back-to-top)

---

## <a id="full-context-fidelity-cost">What Fidelity and Startup Cost Come with a Full Context?</a>

<details>
<summary>Click for details</summary>
A full context gives high fidelity because many production beans and auto-configurations are present together. The cost is startup time, more potential external dependencies, and a larger failure surface. Spring's context cache can amortize that startup cost when tests share the same effective configuration.

Every unnecessary variation in properties, profiles, mocks, or imported configuration can create a distinct cached context. Full-context tests therefore benefit from stable, reusable configuration just as much as they benefit from realistic wiring.

</details>

- [Back to top](#back-to-top)

---

## <a id="full-context-vs-slice">When Should a Focused Slice Replace `@SpringBootTest`?</a>

<details>
<summary>Click for details</summary>
Use a slice when the test objective belongs to one focused part of the application and unrelated infrastructure would add cost or noise. `@WebMvcTest`, `@WebFluxTest`, `@DataJpaTest`, and `@JdbcTest` deliberately restrict the context and import purpose-specific test auto-configuration.

A slice is not “less correct” when it matches the test boundary. It becomes insufficient only when the behavior depends on omitted collaborators, cross-layer wiring, or full Boot startup semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-environment-handoff">How Does `WebEnvironment` Change the Full-Context Model?</a>

<details>
<summary>Click for details</summary>
`WebEnvironment` determines whether the full Boot context uses a mock web environment, starts an actual embedded server, or disables web infrastructure. `MOCK` is the default. `RANDOM_PORT` and `DEFINED_PORT` create a real server environment, while `NONE` creates a non-web context through `SpringApplication`.

The next chapter focuses on the operational consequences of those modes: client choice, actual ports, and transaction boundaries when requests cross a real HTTP server.

</details>

- [Back to top](#back-to-top)
