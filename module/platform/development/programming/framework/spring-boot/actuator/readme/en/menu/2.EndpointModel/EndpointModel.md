<a id="back-to-top"></a>

# Actuator Endpoint Model

## Menu
- [What Is an Actuator Endpoint?](#actuator-endpoint-abstraction)
- [How Do Endpoint IDs and Operations Structure the Model?](#endpoint-id-and-operations)
- [How Should You Reason About the Built-in Endpoint Catalog?](#built-in-endpoint-catalog)
- [Why Are Actuator Endpoints Technology-Agnostic by Default?](#technology-agnostic-endpoints)
- [Which Endpoint Settings Belong to Actuator and Which Belong to Externalized Configuration?](#endpoint-configuration-boundary)

## <a id="actuator-endpoint-abstraction">What Is an Actuator Endpoint?</a>

<details>
<summary>Click for details</summary>

An Actuator endpoint is a management capability identified by an endpoint ID and composed of one or more operations. The core endpoint model describes what management operation exists; technology-specific adapters decide how that operation is represented over HTTP or JMX.

This is why health, info, metrics, and loggers are better understood first as endpoint IDs than as hard-coded controller paths. In a web application the health endpoint commonly appears under /actuator/health, but the /actuator prefix can change and the same endpoint model may also be exposed through JMX.

The abstraction gives Boot one management model that can be reused across technologies and custom endpoints. It also gives configuration a stable target: properties can enable an endpoint or include/exclude its ID from a particular exposure technology without application code having to implement its own registration logic.

### References

- [Spring Boot 3.3 — Endpoints](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="endpoint-id-and-operations">How Do Endpoint IDs and Operations Structure the Model?</a>

<details>
<summary>Click for details</summary>

The endpoint ID is the stable management identity, such as health, metrics, or loggers. An endpoint can expose read, write, or delete-style operations. For custom endpoints those operation types are expressed with @ReadOperation, @WriteOperation, and @DeleteOperation; built-in endpoints follow the same operation-oriented management model internally.

The operation is deliberately more abstract than an HTTP verb. When exposed over HTTP, Actuator maps the operation to a suitable request method and route. When exposed through JMX, the same management intent becomes an MBean operation. That mapping lets endpoint authors describe management behavior once instead of starting from a web-controller contract.

For learners, the useful mental model is endpoint ID -> operations -> exposure adapter. The URL or MBean name is a representation of that model, not its source identity.

</details>

- [Back to top](#back-to-top)

---

## <a id="built-in-endpoint-catalog">How Should You Reason About the Built-in Endpoint Catalog?</a>

<details>
<summary>Click for details</summary>

Spring Boot provides a catalog of built-in endpoints that cover different operational questions. health reports health information; info reports application metadata; metrics inspects recorded meters; loggers inspects or changes logger configuration; env and configprops reveal configuration-oriented views; beans, mappings, scheduledtasks, conditions, and similar endpoints expose framework/runtime structure.

Diagnostic endpoints have additional prerequisites or risks. startup requires buffered ApplicationStartup data. httpexchanges requires an HttpExchangeRepository. Web applications can expose binary heapdump, logfile content when file logging is configured, and Prometheus-format metrics when the Prometheus registry dependency is present.

Do not memorize the catalog as a flat list. Classify endpoints by the operational question they answer, then check the Boot 3.3 endpoint table for prerequisites, default enablement, and whether the endpoint is web-only or technology-agnostic.

</details>

- [Back to top](#back-to-top)

---

## <a id="technology-agnostic-endpoints">Why Are Actuator Endpoints Technology-Agnostic by Default?</a>

<details>
<summary>Click for details</summary>

The generic @Endpoint model is intentionally independent of Spring MVC, WebFlux, Jersey, or JMX. Boot discovers the endpoint and its operations, then a technology-specific infrastructure layer exposes those operations when that technology is enabled and the endpoint ID is included in its exposure configuration.

This separation matters in libraries and reusable management components. A custom @Endpoint can remain usable over both HTTP and JMX without importing controller annotations or depending on a specific web stack. If an operation truly requires HTTP request/response concepts or a JMX-specific feature, Boot provides narrower extension points for that case.

Technology independence also explains why endpoint payload design should focus on management data rather than HTTP implementation details. The endpoint should first express the operational capability; transport-specific concerns should appear only where the requirement actually needs them.

</details>

- [Back to top](#back-to-top)

---

## <a id="endpoint-configuration-boundary">Which Endpoint Settings Belong to Actuator and Which Belong to Externalized Configuration?</a>

<details>
<summary>Click for details</summary>

Actuator defines the semantics of endpoint configuration: which endpoint ID is enabled, which IDs a web or JMX technology exposes, how a web base path maps endpoint IDs, and endpoint-specific settings such as health detail visibility. Those settings are represented by Boot configuration properties under namespaces such as management.endpoint.* and management.endpoints.*.

The source and precedence of those property values still belong to externalized-configuration. A value may come from a configuration file, environment variable, command-line argument, test override, or another supported property source; Actuator does not redefine how Boot resolves that value.

When debugging configuration, separate the questions. “What does management.endpoints.web.exposure.include mean?” belongs here. “Why did this environment variable override the YAML value?” belongs to externalized configuration. That split avoids duplicating Boot's property-source curriculum inside every management feature.

</details>

- [Back to top](#back-to-top)
