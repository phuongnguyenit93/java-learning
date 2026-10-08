<a id="back-to-top"></a>

# Web application type detection

## Menu
- [NONE, SERVLET, and REACTIVE](#web-application-types)
- [Classpath-based WebApplicationType Deduction](#classpath-type-deduction)
- [When Servlet and Reactive Signals Coexist](#servlet-reactive-precedence)
- [Explicit Override with spring.main.web-application-type](#explicit-type-override)
- [How Application Type Changes Context and Server Startup](#application-type-consequences)

## <a id="web-application-types">NONE, SERVLET, and REACTIVE</a>

<details>
<summary>Click for details</summary>
`WebApplicationType` has three values: `NONE`, `SERVLET`, and `REACTIVE`. `NONE` means Boot should create a non-web application context and not start an embedded web server. `SERVLET` selects the Servlet web runtime. `REACTIVE` selects the reactive web runtime.

Treat this value as an early bootstrap decision. It influences which kind of `ApplicationContext` Spring Boot creates and which conditional web-server auto-configurations are eligible later in startup.

### References

- [Spring Boot 3.3 API — WebApplicationType](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/WebApplicationType.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="classpath-type-deduction">Classpath-based WebApplicationType Deduction</a>

<details>
<summary>Click for details</summary>
If the type is not explicitly configured, `SpringApplication` deduces it from the classpath. Conceptually, a reactive-only web classpath yields `REACTIVE`, a Servlet-capable web classpath yields `SERVLET`, and a classpath without the required web indicators yields `NONE`.

This is why adding or removing starters can alter runtime behavior even when `main` does not change. The dependency graph is one of Boot's decision inputs; the resulting type is then consumed by conditional auto-configuration and context creation.

The exact detection code is intentionally an implementation detail. For application design, remember the observable contract: classpath drives the default and an explicit application type can override that default.

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-reactive-precedence">When Servlet and Reactive Signals Coexist</a>

<details>
<summary>Click for details</summary>
When both Spring MVC and Spring WebFlux are available, Spring Boot chooses the Servlet/MVC application model by default. This is deliberate because applications often add WebFlux only to use `WebClient` while remaining MVC applications.

Therefore, seeing reactive libraries on the classpath does not by itself prove that the application is running as `REACTIVE`. If both web stacks are present and the application is intended to run as WebFlux, make that choice explicit.

### References

- [Spring Boot 3.3 Reference — Reactive Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/reactive.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="explicit-type-override">Explicit Override with spring.main.web-application-type</a>

<details>
<summary>Click for details</summary>
The property `spring.main.web-application-type` can force the bootstrap decision. Common values are `servlet`, `reactive`, and `none`.

```properties
spring.main.web-application-type=reactive
```

Use an override when the classpath is intentionally mixed, or when a web-capable classpath must run without a server. `none` is especially useful for command-line or batch-style modes that reuse application dependencies but should not expose an HTTP endpoint.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-type-consequences">How Application Type Changes Context and Server Startup</a>

<details>
<summary>Click for details</summary>
The selected type changes context construction before the server itself is created. `SERVLET` leads Boot toward a Servlet web application context and a `ServletWebServerFactory`; `REACTIVE` leads toward a reactive web application context and a `ReactiveWebServerFactory`; `NONE` uses a non-web context and does not follow embedded web-server startup.

This also explains why changing only a server dependency is different from changing `WebApplicationType`. Replacing Tomcat with Jetty changes the implementation inside the same Servlet runtime model. Switching from `SERVLET` to `REACTIVE` changes the web runtime model and which auto-configuration path is eligible.

</details>

- [Back to top](#back-to-top)
