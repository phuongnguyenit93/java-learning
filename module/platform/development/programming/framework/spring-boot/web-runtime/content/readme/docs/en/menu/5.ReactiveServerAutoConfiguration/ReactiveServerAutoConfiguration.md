<a id="back-to-top"></a>

# Reactive server auto-configuration

## Menu
- [When Reactive Web Server Auto-configuration Applies](#reactive-server-auto-config-trigger)
- [Why Reactor Netty Is the Default Reactive Server](#reactor-netty-default)
- [Reactive Factories for Netty, Tomcat, Jetty, and Undertow](#reactive-server-factories)
- [Where Server Configuration Fits into Reactive Factory Auto-configuration](#reactive-server-configuration-relation)
- [Back-off When the Application Supplies a Reactive WebServerFactory](#reactive-server-backoff)
- [Reactive Server Bootstrap versus Spring WebFlux Request Processing](#reactive-server-webflux-boundary)

## <a id="reactive-server-auto-config-trigger">When Reactive Web Server Auto-configuration Applies</a>

<details>
<summary>Click for details</summary>
`ReactiveWebServerFactoryAutoConfiguration` is the reactive counterpart to the Servlet server path. It participates for a reactive web application when the required server/runtime classes are present, configures the reactive factory infrastructure, and registers the customizer processing used before the server starts.

The `REACTIVE` application type is therefore a bootstrap input to server selection, not a label applied after a server is already running.

### References

- [Spring Boot 3.3 API — ReactiveWebServerFactoryAutoConfiguration](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/autoconfigure/web/reactive/ReactiveWebServerFactoryAutoConfiguration.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="reactor-netty-default">Why Reactor Netty Is the Default Reactive Server</a>

<details>
<summary>Click for details</summary>
Reactor Netty is the default because `spring-boot-starter-webflux` includes `spring-boot-starter-reactor-netty`. A normal WebFlux application therefore has the classes needed for Boot to auto-configure `NettyReactiveWebServerFactory` without extra server choices.

"Default" is a dependency convention. It does not mean WebFlux requires Reactor Netty: Boot 3.3 can also run the reactive stack on supported Tomcat, Jetty, or Undertow integrations.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-server-factories">Reactive Factories for Netty, Tomcat, Jetty, and Undertow</a>

<details>
<summary>Click for details</summary>
Boot provides `NettyReactiveWebServerFactory`, `TomcatReactiveWebServerFactory`, `JettyReactiveWebServerFactory`, and `UndertowReactiveWebServerFactory`. The chosen implementation follows the reactive application model plus the compatible server classes available on the classpath.

This is why "Tomcat" alone does not imply MVC. Tomcat can host a Servlet application or participate in a reactive server integration; the web application type and matching factory determine which Boot path is in use.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-server-configuration-relation">Where Server Configuration Fits into Reactive Factory Auto-configuration</a>

<details>
<summary>Click for details</summary>
`ServerProperties` also feeds the reactive server path. Boot supplies a `ReactiveWebServerFactoryCustomizer` for portable server settings and additional server-specific customizers for implementation-specific namespaces where supported.

The resulting pattern is the same as the Servlet side: configuration is resolved first, customizers apply it to the selected factory, and only then does the reactive web application context create the server. This shared model lets later chapters discuss common `server.*` settings without duplicating them for both stacks.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-server-backoff">Back-off When the Application Supplies a Reactive WebServerFactory</a>

<details>
<summary>Click for details</summary>
Reactive server factory configuration backs off when the application supplies its own `ReactiveWebServerFactory` bean. Doing so replaces Boot's choice of factory, so it is appropriate only when properties and targeted customizers cannot represent the requirement.

Boot's auto-configured factory customizers still apply to the custom factory. If a replacement factory appears to ignore or override application settings, inspect both the factory's initial state and the ordered customizer chain before assuming auto-configuration has been disabled entirely.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-server-webflux-boundary">Reactive Server Bootstrap versus Spring WebFlux Request Processing</a>

<details>
<summary>Click for details</summary>
Reactive server auto-configuration owns server bootstrap and lifecycle integration. Spring WebFlux owns the HTTP processing model above it: `HttpHandler`, routing, annotated controllers, codecs, filters, reactive composition, and back-pressure semantics.

Keep this boundary visible when tuning the application. A port, TLS, compression, or server resource issue belongs first to the Boot/server runtime layer. A route, codec, or reactive pipeline issue belongs to WebFlux and Reactor learning.

</details>

- [Back to top](#back-to-top)
