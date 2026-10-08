<a id="back-to-top"></a>

# Management Surface Access and Security Boundaries

## Menu
- [Why Should the Remotely Exposed Endpoint Set Stay Minimal?](#minimal-management-exposure)
- [How Can the Management Web Base Path Be Separated from the Application Surface?](#management-base-path)
- [How Do a Separate Management Port and Address Change Network Placement?](#management-port-address)
- [Why Are Endpoint Exposure and Authorization Different Decisions?](#exposure-vs-authorization)
- [What Security Behavior Does Boot Provide When Spring Security Is Present?](#actuator-security-auto-configuration)
- [Where Does Actuator Security Integration Hand Off to Spring Security?](#spring-security-handoff)

## <a id="minimal-management-exposure">Why Should the Remotely Exposed Endpoint Set Stay Minimal?</a>

<details>
<summary>Click for details</summary>

The safest management endpoint is one the deployment does not expose unless it has an operational reason to do so. Spring Boot 3.3's default of exposing only health over HTTP and JMX establishes that posture. Every additional endpoint should be justified by a consumer, an incident workflow, or an automation requirement.

Minimal exposure reduces both information disclosure and the number of privileged operations that need security review. It also makes network policy easier to understand: a small set of management URLs has a clearer purpose than an unrestricted /actuator tree.

Review exposure when capabilities change. Adding a registry, repository, logging feature, or custom endpoint can change what an endpoint reveals. A historical wildcard include should not silently make new diagnostics remotely reachable after an upgrade.

</details>

- [Back to top](#back-to-top)

---

## <a id="management-base-path">How Can the Management Web Base Path Be Separated from the Application Surface?</a>

<details>
<summary>Click for details</summary>

management.endpoints.web.base-path controls the common HTTP prefix for Actuator web endpoints. The default is /actuator, but an application can move the management surface to a path such as /manage. Individual endpoint paths can also be remapped when an infrastructure convention requires it. When `management.server.port` creates a separate management server, this endpoint base path is relative to `management.server.base-path`.

Changing the path is useful for routing and organization, but it is not a security boundary by itself. An attacker does not lose access merely because /actuator became /manage. Authorization and network controls must still protect the surface.

The path is also separate from the endpoint ID. Health remains the health endpoint after its URL moves. Keep configuration, documentation, monitoring checks, and reverse-proxy routes aligned so operators do not diagnose a path mismatch as an endpoint failure.

### References

- [Spring Boot 3.3 — Monitoring and Management over HTTP](https://docs.spring.io/spring-boot/3.3/reference/actuator/monitoring.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="management-port-address">How Do a Separate Management Port and Address Change Network Placement?</a>

<details>
<summary>Click for details</summary>

management.server.port can place HTTP management endpoints on a port different from the application's main server port. This creates a separate management web server context and allows infrastructure to route or firewall operational traffic independently from business traffic. On that separate server, `management.server.base-path` defines the management-server base and `management.endpoints.web.base-path` is resolved relative to it.

When the management port differs, management.server.address can bind that server to a specific interface, for example localhost or an internal operations network. Boot only supports choosing a different management address when the management port is different from the main server port.

Separate placement changes the failure domain as well as security. A healthy management server does not prove the main application connector is healthy. Health probes that must validate the main request path may need the additional liveness/readiness paths on the main port rather than relying only on the separate management listener.

</details>

- [Back to top](#back-to-top)

---

## <a id="exposure-vs-authorization">Why Are Endpoint Exposure and Authorization Different Decisions?</a>

<details>
<summary>Click for details</summary>

Exposure and authorization answer different questions. Exposure decides whether an endpoint is reachable through a management technology. Authorization decides whether a caller that reaches the surface is permitted to invoke the operation. An endpoint can be exposed but denied to a caller, or enabled but not exposed at all.

Network placement adds another independent layer. A management port may be reachable only from an operations subnet even before application-level security runs. Sanitization and health-detail visibility further reduce what an authorized or unauthenticated caller sees.

Design these controls as defense in depth rather than substitutes. Hiding an endpoint through a nonstandard path is not authorization; requiring authentication does not justify exposing every endpoint to the public internet; excluding an endpoint from HTTP does not necessarily disable it in the application.

</details>

- [Back to top](#back-to-top)

---

## <a id="actuator-security-auto-configuration">What Security Behavior Does Boot Provide When Spring Security Is Present?</a>

<details>
<summary>Click for details</summary>

When Spring Security is on the classpath and the application has not defined its own SecurityFilterChain, Spring Boot provides security auto-configuration for Actuator. In the Boot 3.3 model, actuators other than the health endpoint are secured by the default security arrangement, while health can remain available for basic operational checks.

If the application defines a custom SecurityFilterChain bean, Boot backs off from its default actuator security so the application controls the rules. Boot provides Actuator-aware request matchers such as EndpointRequest that can help a Spring Security configuration target management endpoints.

The important operational consequence is ownership. Adding an application SecurityFilterChain means the team must deliberately include the desired Actuator policy. Do not assume Boot's previous default restrictions remain in force after security customization.

### References

- [Spring Boot 3.3 — Actuator Security](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-security-handoff">Where Does Actuator Security Integration Hand Off to Spring Security?</a>

<details>
<summary>Click for details</summary>

Actuator owns which endpoint capabilities exist, which transports expose them, and the Boot integration points that make management requests matchable by security configuration. Spring Security owns how identities are authenticated and how authorization decisions are implemented in filter chains and request rules.

This module can explain that health may be permitted more broadly while loggers, env, heapdump, or custom write operations need stronger protection. It should not re-teach users, roles, authentication providers, CSRF, session policy, OAuth2 resource servers, matcher ordering, or method security.

When a request receives 401 or 403 after endpoint availability/exposure is confirmed, the investigation has crossed into Spring Security policy. Follow the active SecurityFilterChain and authentication context there rather than changing Actuator exposure until the endpoint becomes public.

</details>

- [Back to top](#back-to-top)
