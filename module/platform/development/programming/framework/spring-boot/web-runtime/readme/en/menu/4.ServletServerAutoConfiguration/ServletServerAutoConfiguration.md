<a id="back-to-top"></a>

# Servlet server auto-configuration

## Menu
- [When Servlet Web Server Auto-configuration Applies](#servlet-server-auto-config-trigger)
- [Tomcat, Jetty, and Undertow Servlet Factories](#servlet-server-factories)
- [Where Server Configuration Fits into Servlet Factory Auto-configuration](#servlet-server-configuration-relation)
- [Back-off When the Application Supplies a Servlet WebServerFactory](#servlet-server-backoff)
- [Boot and User Customizers around the Servlet Factory](#servlet-server-customizer-chain)
- [Servlet Server Bootstrap versus Spring MVC Request Processing](#servlet-server-mvc-boundary)

## <a id="servlet-server-auto-config-trigger">When Servlet Web Server Auto-configuration Applies</a>

<details>
<summary>Click for details</summary>
`ServletWebServerFactoryAutoConfiguration` participates when Boot is running a Servlet web application and the relevant server classes are present. Its job is to assemble Boot's embedded Servlet-server infrastructure, including configuration properties and the customizer processing needed by the selected factory.

The important condition is the runtime model established earlier. A Servlet server factory should not appear merely because a server jar exists in an application that is intentionally non-web or reactive.

### References

- [Spring Boot 3.3 API — ServletWebServerFactoryAutoConfiguration](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/autoconfigure/web/servlet/ServletWebServerFactoryAutoConfiguration.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-server-factories">Tomcat, Jetty, and Undertow Servlet Factories</a>

<details>
<summary>Click for details</summary>
Boot provides concrete Servlet factories for its supported embedded containers: `TomcatServletWebServerFactory`, `JettyServletWebServerFactory`, and `UndertowServletWebServerFactory`. Classpath conditions determine which implementation-specific factory configuration is eligible.

Applications normally reach these factories indirectly through starters. That is why replacing the server dependency is the preferred first step: it changes the eligible implementation without replacing Boot's lifecycle and configuration machinery.

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-server-configuration-relation">Where Server Configuration Fits into Servlet Factory Auto-configuration</a>

<details>
<summary>Click for details</summary>
The auto-configuration does more than instantiate a factory. Boot binds `server.*` configuration into `ServerProperties` and provides customizers that apply common and server-specific settings to the factory before the server is created.

Think of the flow as `external configuration → ServerProperties → Boot customizers → ServletWebServerFactory → WebServer`. The details of property source precedence and relaxed binding remain in the `externalized-configuration` module; this module focuses on how the resulting server configuration affects the web runtime.

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-server-backoff">Back-off When the Application Supplies a Servlet WebServerFactory</a>

<details>
<summary>Click for details</summary>
The embedded Servlet factory configurations are designed to back off when the application provides its own `ServletWebServerFactory` bean. This is a powerful replacement hook because the application has taken responsibility for choosing and constructing the factory.

Use that hook deliberately. Supplying a factory bean is a stronger intervention than setting properties or adding a customizer, and it can reduce portability. Boot's auto-configured `WebServerFactoryCustomizer` beans still apply to a custom factory, so a replacement factory does not imply that every Boot customization disappears.

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-server-customizer-chain">Boot and User Customizers around the Servlet Factory</a>

<details>
<summary>Click for details</summary>
Boot and application customizers are collected and applied to the chosen `ServletWebServerFactory` before it creates the server. Boot's own auto-configured `WebServerFactoryCustomizer` beans use order `0`; user customizers without a more specific order therefore run after those Boot customizers.

Ordering matters when two customizers change the same setting. Prefer configuration properties for supported settings; use an explicitly ordered customizer only when code must intentionally refine or override the factory state.

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-server-mvc-boundary">Servlet Server Bootstrap versus Spring MVC Request Processing</a>

<details>
<summary>Click for details</summary>
Servlet server auto-configuration gets a Servlet-capable server running and connects it to Boot's lifecycle. Spring MVC request processing begins at a different ownership boundary: `DispatcherServlet`, handler mappings, controllers, argument resolution, message conversion, interceptors, and MVC error handling belong to Spring Framework web learning.

The distinction is useful in debugging. If the application never binds a port, inspect application type, server dependency, factory auto-configuration, and server settings. If the server accepts a connection but a controller mapping behaves incorrectly, move the investigation into the MVC request-processing layer.

</details>

- [Back to top](#back-to-top)
