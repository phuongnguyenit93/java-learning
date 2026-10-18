<a id="back-to-top"></a>

# Endpoint Enablement, Availability, and Exposure

## Menu
- [What Does Enabling or Disabling an Endpoint Change?](#endpoint-enablement)
- [How Is Exposure Different from Enablement?](#endpoint-exposure)
- [When Is an Endpoint Actually Available Through a Management Technology?](#endpoint-availability)
- [How Are Endpoints Exposed over HTTP?](#web-endpoint-surface)
- [How Are Endpoints Exposed over JMX?](#jmx-endpoint-surface)
- [How Does the Default /actuator Web Base Path Fit the Endpoint Model?](#actuator-base-path)
- [Why Should Remote Endpoint Exposure Be Deliberate?](#exposure-defaults-and-risk)

## <a id="endpoint-enablement">What Does Enabling or Disabling an Endpoint Change?</a>

<details>
<summary>Click for details</summary>

Endpoint enablement answers whether the endpoint capability exists in the application at all. In Spring Boot 3.3, most built-in endpoints are enabled by default, while operations with greater impact such as shutdown are disabled by default. An individual endpoint can be controlled with management.endpoint.<id>.enabled, and management.endpoints.enabled-by-default can invert the baseline for all endpoints.

When an endpoint is disabled, Boot removes that endpoint from the application context rather than merely hiding its URL. This matters because code and auto-configuration can no longer rely on the endpoint bean being present. A common least-capability pattern is to disable endpoints by default and explicitly enable only the few required by the deployment.

Enablement should therefore answer “does this management capability exist?” It should not be used as a substitute for choosing which transport exposes the endpoint or which caller is authorized to use it.

</details>

- [Back to top](#back-to-top)

---

## <a id="endpoint-exposure">How Is Exposure Different from Enablement?</a>

<details>
<summary>Click for details</summary>

Exposure answers a different question: over which management technology can an enabled endpoint be reached? Spring Boot 3.3 has technology-specific include/exclude properties for web and JMX exposure. For example, management.endpoints.web.exposure.include selects endpoint IDs exposed over HTTP, while the matching exclude list can remove IDs and takes precedence over include.

By default, only the health endpoint is exposed over HTTP and JMX. Expanding the include list is therefore an explicit production decision. The wildcard can select all endpoints, but doing so should trigger a review of sensitive and write-capable endpoints rather than becoming a convenience default.

An endpoint can be enabled but not exposed. That state is useful when a capability may be consumed internally or when one transport should remain unavailable. Keep enablement, exposure, and authorization as separate axes.

### References

- [Spring Boot 3.3 — Exposing Endpoints](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="endpoint-availability">When Is an Endpoint Actually Available Through a Management Technology?</a>

<details>
<summary>Click for details</summary>

In the Boot 3.3 endpoint model, an endpoint is available through a management technology when it is enabled and exposed through that technology. Built-in endpoint auto-configuration is conditional on availability, so a disabled or non-exposed endpoint may not result in the management component a learner expects to find.

Availability is also technology-specific. A health endpoint can be exposed over HTTP but not JMX, or vice versa. JMX has an additional platform prerequisite because Spring JMX support itself must be enabled before JMX endpoint exposure can be useful.

When an expected endpoint is “missing”, diagnose in order: is the endpoint enabled, is the intended transport active, is the endpoint ID included rather than excluded from that transport, and only then ask whether network routing or authorization blocks access. This sequence avoids treating every 404 or absent MBean as a security failure.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-endpoint-surface">How Are Endpoints Exposed over HTTP?</a>

<details>
<summary>Click for details</summary>

For a web application, Actuator can expose endpoints through Spring MVC, Spring WebFlux, or Jersey. If both Jersey and Spring MVC are available, Spring MVC is used. The web layer maps endpoint IDs and operations into HTTP routes and response representations; Jackson is needed for the documented JSON representations.

HTTP exposure is configured independently from the business controller surface. An endpoint included in management.endpoints.web.exposure.include becomes part of the management web surface if it is enabled and its prerequisites are satisfied. Read/write/delete-style endpoint operations are translated into suitable HTTP methods by the Actuator web infrastructure.

This chapter owns the management mapping, not generic MVC/WebFlux request-processing semantics. Controller dispatch, reactive processing, filters, and other framework internals remain with the corresponding Spring Framework owners.

</details>

- [Back to top](#back-to-top)

---

## <a id="jmx-endpoint-surface">How Are Endpoints Exposed over JMX?</a>

<details>
<summary>Click for details</summary>

JMX provides a second management representation. Spring's JMX support is disabled by default and can be enabled with spring.jmx.enabled=true. When active, Actuator endpoints selected by the JMX exposure configuration are published as MBeans, by default under the org.springframework.boot domain.

The endpoint ID participates in the MBean identity, while endpoint operations become management operations rather than HTTP requests. This reinforces the value of the technology-agnostic endpoint model: the same logical capability can be available through a non-web management channel.

JMX exposure is not automatically “safer” merely because it is not HTTP. Remote JMX connectivity, credentials, network policy, and platform configuration still need deliberate security. Those generic JMX concerns are outside this Actuator curriculum; here the focus is Boot's endpoint-to-JMX integration.

</details>

- [Back to top](#back-to-top)

---

## <a id="actuator-base-path">How Does the Default /actuator Web Base Path Fit the Endpoint Model?</a>

<details>
<summary>Click for details</summary>

For HTTP management, the default convention places endpoint routes beneath /actuator. An endpoint with ID health is therefore normally available at /actuator/health when it is exposed. management.endpoints.web.base-path can change that common prefix, while management.endpoints.web.path-mapping can remap an individual endpoint path.

The base path is part of endpoint routing, not an endpoint ID. Changing /actuator to /manage does not rename the health endpoint and does not change which endpoint properties use the ID health. This separation is useful when a reverse proxy or organizational routing convention requires a different URL layout.

When management endpoints use the main server port, the base path is relative to the application's web base/context path. When `management.server.port` creates a separate management server, `management.endpoints.web.base-path` is relative to `management.server.base-path` on that management server.

### References

- [Spring Boot 3.3 — Monitoring and Management over HTTP](https://docs.spring.io/spring-boot/3.3/reference/actuator/monitoring.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="exposure-defaults-and-risk">Why Should Remote Endpoint Exposure Be Deliberate?</a>

<details>
<summary>Click for details</summary>

Actuator endpoints can reveal internal configuration, bean structure, runtime metrics, logging controls, thread state, or binary heap contents. The fact that an endpoint is useful during an incident is exactly why broad exposure can be risky. Boot's narrow default exposure gives the application owner a chance to choose which capabilities need remote reachability.

Use include lists that express an operational need rather than “expose everything and secure it later”. Exclude remains useful as a safety override, but a small positive set is easier to review. Write-capable operations and dump/configuration endpoints deserve especially explicit treatment.

Exposure is only reachability. A remotely exposed endpoint may still need network isolation and authorization, and a non-exposed endpoint may still exist in the context. The management-access chapter later combines these dimensions without turning this module into a Spring Security course.

</details>

- [Back to top](#back-to-top)
