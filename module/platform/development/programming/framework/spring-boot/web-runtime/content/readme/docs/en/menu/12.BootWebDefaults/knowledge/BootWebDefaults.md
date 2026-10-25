<a id="back-to-top"></a>

# Boot web defaults and Spring Framework ownership boundaries

## Menu
- [The Boot Web Defaults to Recognize](#boot-web-defaults-map)
- [Convention versus Explicit Web-runtime Control](#convention-vs-explicit-web-control)
- [Handoff to Spring MVC and Spring WebFlux](#spring-framework-web-handoff)
- [Handoff to Servlet and Reactive Server Internals](#container-internals-handoff)
- [Handoff to HTTP, TLS, and Reverse-proxy Infrastructure](#network-security-handoff)
- [End-to-end Boot Web Runtime Decision Model](#web-runtime-end-to-end-model)

## <a id="boot-web-defaults-map">The Boot Web Defaults to Recognize</a>

<details>
<summary>Click for details</summary>
Several conventions explain the first successful run of a Boot web application: the classpath drives `WebApplicationType` unless overridden; `spring-boot-starter-web` selects Servlet/MVC with Tomcat by default; `spring-boot-starter-webflux` selects the reactive model with Reactor Netty when MVC is absent; the main HTTP port defaults to `8080`.

Other defaults matter in production: response compression is disabled until enabled, `server.shutdown` is `immediate` in Boot 3.3, and forwarded-header processing is normally `NONE` except on supported cloud platforms where Boot defaults to `NATIVE`. These are conventions to recognize, not assumptions to hide from deployment configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="convention-vs-explicit-web-control">Convention versus Explicit Web-runtime Control</a>

<details>
<summary>Click for details</summary>
Boot's web runtime works best when conventions express the common case and configuration makes deployment choices explicit. Dependencies select a supported server, `server.*` properties express common behavior, server-specific namespaces handle implementation details, and customizers cover gaps in the property model.

Move toward explicit control when the deployment has a real reason: a mixed MVC/WebFlux classpath, a non-default server, a fixed bind address, TLS material, proxy forwarding, extra connectors, or graceful shutdown. Keeping the decision at the highest available abstraction makes future upgrades and server changes easier to reason about.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-framework-web-handoff">Handoff to Spring MVC and Spring WebFlux</a>

<details>
<summary>Click for details</summary>
Once Boot has selected the web runtime, started the server, and connected it to the application context, request-processing semantics belong to Spring Framework. MVC owns `DispatcherServlet`, controller mappings, converters, interceptors, and the Servlet web framework model. WebFlux owns reactive routing/handlers, codecs, filters, and its reactive processing model.

Boot adds auto-configuration and sensible defaults around those frameworks, but this module stops at the runtime/server integration boundary. A server that is listening correctly can coexist with a broken handler mapping; the two failures belong to different layers.

</details>

- [Back to top](#back-to-top)

---

## <a id="container-internals-handoff">Handoff to Servlet and Reactive Server Internals</a>

<details>
<summary>Click for details</summary>
Tomcat, Jetty, Undertow, and Reactor Netty have their own connectors, handlers, workers, event loops, queues, protocol implementations, and tuning models. Boot provides properties, factories, and customizer hooks into those systems but does not make their internals portable.

Learn enough server-specific API to configure a requirement through Boot, then move deep container/runtime reasoning to the appropriate owner. This keeps the Boot curriculum centered on selection, configuration, lifecycle, and integration.

</details>

- [Back to top](#back-to-top)

---

## <a id="network-security-handoff">Handoff to HTTP, TLS, and Reverse-proxy Infrastructure</a>

<details>
<summary>Click for details</summary>
Boot can enable compression or HTTP/2, attach certificate material, consume SSL bundles, interpret forwarded headers, and expose server-specific proxy settings. Those are integration points into larger domains.

HTTP protocol mechanics, TLS/PKI theory, certificate operations, reverse-proxy routing, load-balancer behavior, network trust, and operating-system socket tuning remain infrastructure/security subjects. The Boot learner should know which property or hook connects to them and when deeper ownership begins.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-runtime-end-to-end-model">End-to-end Boot Web Runtime Decision Model</a>

<details>
<summary>Click for details</summary>
Use this decision sequence when designing or debugging a Boot web runtime:

```text
1. What WebApplicationType should this process use?
        ↓
2. Which supported embedded server is on the classpath?
        ↓
3. Did the matching Servlet/Reactive factory auto-configuration apply?
        ↓
4. Can server.* or a server-specific property express the requirement?
        ↓
5. If not, which WebServerFactoryCustomizer is the narrowest hook?
        ↓
6. Does deployment require HTTP/2, TLS/SNI, or forwarded headers?
        ↓
7. How will the server stop, and is graceful shutdown required?
        ↓
8. Is the remaining problem actually MVC/WebFlux, server internals,
   or network/security infrastructure?
```

This model keeps Boot-specific decisions connected from startup to shutdown. It also provides a debugging boundary: identify the last stage that is known to work, then continue in the owning layer instead of treating every web problem as a controller or server problem.

### References

- [Spring Boot 3.3 Reference — Web](https://docs.spring.io/spring-boot/3.3/reference/web/)
- [Spring Boot 3.3 How-to — Embedded Web Servers](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html)

</details>

- [Back to top](#back-to-top)
