<a id="back-to-top"></a>

# Server properties and configuration model

## Menu
- [ServerProperties as Boot's Server Configuration Model](#server-properties-model)
- [Portable Controls in the server.* Namespace](#common-server-namespace)
- [Port, Bind Address, and HTTP Endpoint Controls](#port-address-endpoint-controls)
- [server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*](#server-specific-namespaces)
- [Choosing Common versus Server-specific Properties](#portable-vs-server-specific-properties)
- [Configuration Precedence and Binding Belong to Externalized Configuration](#external-configuration-handoff)

## <a id="server-properties-model">ServerProperties as Boot's Server Configuration Model</a>

<details>
<summary>Click for details</summary>
`ServerProperties` is Boot's configuration-properties model for the embedded server. It gives auto-configured server customizers a structured view of values under `server.*`, including common settings and implementation-specific groups.

This is a useful boundary: application configuration expresses desired server behavior, `ServerProperties` represents the bound Boot model, and customizers translate that model into the selected factory. Application code normally does not need to inject `ServerProperties` merely to change a setting that can already be declared externally.

### References

- [Spring Boot 3.3 Common Application Properties — Server Properties](https://docs.spring.io/spring-boot/3.3/appendix/application-properties/)

</details>

- [Back to top](#back-to-top)

---

## <a id="common-server-namespace">Portable Controls in the server.* Namespace</a>

<details>
<summary>Click for details</summary>
Portable settings live directly under `server.*` when Boot can model the same intent across supported servers. Examples include `server.port`, `server.address`, `server.compression.*`, `server.http2.enabled`, `server.max-http-request-header-size`, `server.shutdown`, `server.forward-headers-strategy`, and `server.ssl.*`.

Portable does not mean every server implements the feature identically. It means Boot offers one configuration intent and adapts it where the selected server supports that capability.

</details>

- [Back to top](#back-to-top)

---

## <a id="port-address-endpoint-controls">Port, Bind Address, and HTTP Endpoint Controls</a>

<details>
<summary>Click for details</summary>
The main HTTP port defaults to `8080` in a standalone web application. Set `server.port` to a fixed value, `0` to ask the operating system for an available port, or `-1` to create a web application context without opening HTTP endpoints. `server.address` controls the network address to which the server binds.

These settings are server runtime controls, not routing controls. Changing the port or bind address changes where the server listens; it does not change Spring MVC or WebFlux route mappings.

</details>

- [Back to top](#back-to-top)

---

## <a id="server-specific-namespaces">server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*</a>

<details>
<summary>Click for details</summary>
Server-specific namespaces expose capabilities that cannot be represented cleanly as one portable property model. Boot 3.3 provides groups such as `server.tomcat.*`, `server.jetty.*`, `server.undertow.*`, and `server.netty.*`.

Examples include Tomcat connection queues and remote-IP valve settings, Jetty access-log settings, Undertow worker/options configuration, and Netty connection/resource settings. Once you enter one of these namespaces, the application is intentionally coupling that configuration to a particular embedded server family.

</details>

- [Back to top](#back-to-top)

---

## <a id="portable-vs-server-specific-properties">Choosing Common versus Server-specific Properties</a>

<details>
<summary>Click for details</summary>
Start with a common `server.*` property when it expresses the requirement. This keeps the application easier to move between supported servers and makes the intent visible without server API knowledge. Use a server-specific property when the requirement itself depends on that implementation or when the common model does not expose the needed control.

If neither property level is sufficient, move to a `WebServerFactoryCustomizer`. This progression keeps customization as declarative and portable as the requirement allows.

</details>

- [Back to top](#back-to-top)

---

## <a id="external-configuration-handoff">Configuration Precedence and Binding Belong to Externalized Configuration</a>

<details>
<summary>Click for details</summary>
This module consumes the result of Boot's externalized configuration system; it does not redefine configuration precedence, profile activation, relaxed binding, environment variables, or property-source ordering. Those rules determine **which value wins** before server customization uses it.

For example, `SERVER_PORT` can bind to `server.port`, but why an environment variable overrides or loses to another source belongs to the `externalized-configuration` module. Here the learning question is what the resolved `server.port` value does to the embedded server.

</details>

- [Back to top](#back-to-top)
