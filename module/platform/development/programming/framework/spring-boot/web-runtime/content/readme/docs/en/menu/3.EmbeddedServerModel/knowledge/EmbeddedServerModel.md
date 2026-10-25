<a id="back-to-top"></a>

# Embedded server model

## Menu
- [Web Starters and Their Default Embedded Servers](#starter-server-defaults)
- [Supported Server Choices for Servlet and Reactive Applications](#supported-server-choices)
- [Replacing the Default Embedded Server Dependency](#replacing-default-server)
- [The Role of WebServerFactory](#web-server-factory-role)
- [How the Web Application Context Starts the Server](#web-server-context-startup)
- [Boot Selection versus Server Implementation Internals](#server-selection-boundary)

## <a id="starter-server-defaults">Web Starters and Their Default Embedded Servers</a>

<details>
<summary>Click for details</summary>
`spring-boot-starter-web` brings Tomcat through `spring-boot-starter-tomcat`, so Tomcat is the conventional embedded server for Servlet applications. `spring-boot-starter-webflux` brings Reactor Netty through `spring-boot-starter-reactor-netty`, so Reactor Netty is the conventional reactive server.

These are starter defaults, not hard-coded requirements. Boot's web runtime is designed so that the server implementation can be replaced while the surrounding application remains a Boot application.

### References

- [Spring Boot 3.3 How-to — Embedded Web Servers](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="supported-server-choices">Supported Server Choices for Servlet and Reactive Applications</a>

<details>
<summary>Click for details</summary>
For the Servlet stack, Boot 3.3 supports embedded Tomcat, Jetty, and Undertow. For the reactive stack, it supports Reactor Netty as well as reactive adapters for Tomcat, Jetty, and Undertow.

The same server name can therefore appear in different runtime models. `TomcatServletWebServerFactory` and `TomcatReactiveWebServerFactory`, for example, are different Boot integrations. Choose the web stack first, then the concrete server implementation within that stack.

</details>

- [Back to top](#back-to-top)

---

## <a id="replacing-default-server">Replacing the Default Embedded Server Dependency</a>

<details>
<summary>Click for details</summary>
Replacing a server is normally a dependency decision. Remove or replace the starter-provided default and add the starter for the server you want. For example, a Servlet application can replace `spring-boot-starter-tomcat` with `spring-boot-starter-jetty`; a WebFlux application can replace Reactor Netty with Undertow.

This keeps Boot's auto-configuration model intact. The classpath now exposes a different supported server implementation, so the matching factory configuration becomes eligible. Avoid writing server bootstrap code merely to perform a dependency swap that Boot already supports.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-server-factory-role">The Role of WebServerFactory</a>

<details>
<summary>Click for details</summary>
`WebServerFactory` is Boot's abstraction for creating the runtime `WebServer`. Servlet applications work with `ServletWebServerFactory`; reactive applications work with `ReactiveWebServerFactory`. Concrete factories such as `TomcatServletWebServerFactory` or `NettyReactiveWebServerFactory` adapt that abstraction to a server implementation.

The factory is the customization point before the server exists. Properties and `WebServerFactoryCustomizer` beans modify the factory; the web application context later asks the factory to create the actual server.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-server-context-startup">How the Web Application Context Starts the Server</a>

<details>
<summary>Click for details</summary>
A web-aware Boot `ApplicationContext` coordinates server creation with context refresh. Once the required factory and application infrastructure are ready, the context obtains a `WebServer` from the factory and starts it as part of Boot's managed lifecycle.

The server's actual bound port may only be known after initialization, especially with `server.port=0`. Boot publishes a `WebServerInitializedEvent` after the server is ready, and the `WebServerApplicationContext` exposes the server for runtime inspection.

</details>

- [Back to top](#back-to-top)

---

## <a id="server-selection-boundary">Boot Selection versus Server Implementation Internals</a>

<details>
<summary>Click for details</summary>
Boot owns the selection and integration contract: which supported server factory is eligible, which configuration is applied, how customizers participate, and how the server joins application startup and shutdown. It does not redefine how Tomcat connectors, Jetty handlers, Undertow workers, or Netty event loops work internally.

When server-specific internals matter, use them through the narrowest Boot customization hook that solves the requirement and continue deeper learning in the server/runtime owner. That preserves portability and keeps application code from depending on implementation details unnecessarily.

</details>

- [Back to top](#back-to-top)
