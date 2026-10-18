<a id="back-to-top"></a>

# Spring Boot web runtime

## Menu
- [What Does Spring Boot Own in the Web Runtime?](#web-runtime-role)
- [Why Does Boot Need a Web Runtime Layer?](#web-runtime-problem)
- [From SpringApplication to a Running Embedded Server](#boot-to-server-flow)
- [Servlet and Reactive as Runtime Models](#servlet-reactive-runtime-model)
- [Where Boot Web Runtime Stops](#web-runtime-boundaries)
- [How the Web Runtime Chapters Fit Together](#web-runtime-learning-path)

## <a id="web-runtime-role">What Does Spring Boot Own in the Web Runtime?</a>

<details>
<summary>Click for details</summary>
Spring Boot's web runtime is the integration layer that turns a normal Boot application into a process that owns and starts an HTTP server. It decides whether the application is web-capable, creates the appropriate web-aware `ApplicationContext`, auto-configures an embedded server factory, applies configuration and customizers, and manages the resulting server through startup and shutdown.

That responsibility is narrower than "everything web". Boot connects application bootstrap, configuration, auto-configuration, and a supported server implementation. Request mapping, controller invocation, codecs, filters, reactive operators, and other request-processing mechanics remain responsibilities of Spring MVC, Spring WebFlux, or the underlying server.

### References

- [Spring Boot 3.3 Reference — Web](https://docs.spring.io/spring-boot/3.3/reference/web/)

</details>

- [Back to top](#back-to-top)

---

## <a id="web-runtime-problem">Why Does Boot Need a Web Runtime Layer?</a>

<details>
<summary>Click for details</summary>
Without Boot's web runtime layer, an application would have to assemble several infrastructure decisions itself: which server implementation to use, how to construct it, how configuration reaches it, when it starts relative to the Spring context, and how it stops with the application. Boot turns those decisions into conventions that can usually be changed through dependencies and configuration instead of bespoke bootstrap code.

This layer matters because "web framework" and "web server" are separate concerns. Spring MVC or WebFlux can define how requests are processed, while Boot makes a runnable application by attaching those framework facilities to an embedded server lifecycle.

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-to-server-flow">From SpringApplication to a Running Embedded Server</a>

<details>
<summary>Click for details</summary>
The simplified runtime path is:

```text
SpringApplication.run(...)
        ↓
deduce or use configured WebApplicationType
        ↓
create Servlet or Reactive web ApplicationContext
        ↓
apply web-server auto-configuration
        ↓
obtain a WebServerFactory
        ↓
apply ServerProperties + WebServerFactoryCustomizer beans
        ↓
create and start WebServer during context refresh
```

The important mental model is that the embedded server is part of the Boot-managed application lifecycle. You do not normally write a separate `main` method for Tomcat, Jetty, Undertow, or Reactor Netty; the selected web application context coordinates that server with the Spring context.

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-reactive-runtime-model">Servlet and Reactive as Runtime Models</a>

<details>
<summary>Click for details</summary>
Boot supports two web runtime models. A **Servlet** application uses a Servlet-capable server and a `ServletWebServerApplicationContext`. A **Reactive** application uses a reactive web server and a `ReactiveWebServerApplicationContext`. These choices affect server factories, auto-configuration, and server integration.

They do not mean that this module owns Servlet API semantics or reactive request processing. The runtime choice answers "what kind of web application should Boot start?"; Spring MVC and Spring WebFlux answer "how does the framework process a request once the runtime is active?"

</details>

- [Back to top](#back-to-top)

---

## <a id="web-runtime-boundaries">Where Boot Web Runtime Stops</a>

<details>
<summary>Click for details</summary>
This module owns Boot-specific decisions around web application type, embedded server selection, server auto-configuration, `server.*` properties, programmatic server customization, server-level HTTP features, TLS consumption, forwarded headers, and graceful shutdown.

When a question becomes about handler mappings, controllers, filters in the framework request chain, codecs, reactive operators, Servlet container threading internals, HTTP protocol theory, certificate-chain theory, or reverse-proxy implementation, follow the owning curriculum instead. Keeping that boundary clear prevents a server-bootstrap chapter from becoming a second MVC, WebFlux, networking, or container course.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-runtime-learning-path">How the Web Runtime Chapters Fit Together</a>

<details>
<summary>Click for details</summary>
The chapters follow the same order in which production decisions usually appear. First understand the application type and server chosen from the classpath. Then learn how Boot creates the corresponding server factory, how properties and customizers change it, and which server-level HTTP capabilities are portable.

After the local server model is stable, add deployment concerns: TLS and SSL bundles, forwarded headers behind proxies, and graceful shutdown. The final chapter folds those decisions back into one end-to-end model and identifies where Spring Framework or infrastructure-specific learning continues.

</details>

- [Back to top](#back-to-top)
