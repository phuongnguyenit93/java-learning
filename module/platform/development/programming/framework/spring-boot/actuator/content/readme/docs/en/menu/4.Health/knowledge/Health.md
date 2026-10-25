<a id="back-to-top"></a>

# Health Endpoint and Health Contributors

## Menu
- [What Does the Health Endpoint Represent?](#health-endpoint-purpose)
- [How Do HealthContributor and HealthIndicator Fit Together?](#health-contributor-model)
- [How Do Composite Health Contributors Form a Health Tree?](#composite-health-contributors)
- [How Is the Overall Health Status Aggregated?](#health-status-aggregation)
- [How Should Health Components and Details Be Exposed?](#health-detail-visibility)
- [When Should You Add a Custom Health Indicator?](#custom-health-indicators)

## <a id="health-endpoint-purpose">What Does the Health Endpoint Represent?</a>

<details>
<summary>Click for details</summary>

The health endpoint summarizes whether the running application and its relevant components report usable operational status. It is commonly consumed by monitoring systems, deployment platforms, and humans during incidents. Its purpose is to provide a management view of health evidence, not to prove that every business transaction will succeed.

Spring Boot assembles health information from registered HealthContributor instances. Auto-configuration contributes indicators for technologies that are present and supported, while the application can add domain-specific indicators when there is a meaningful operational condition that Boot cannot infer.

The endpoint can return only an overall status or progressively richer component/detail information depending on configuration and authorization. That separation lets a public or infrastructure-facing probe remain small while privileged operators receive deeper evidence.

### References

- [Spring Boot 3.3 — Health Information](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="health-contributor-model">How Do HealthContributor and HealthIndicator Fit Together?</a>

<details>
<summary>Click for details</summary>

HealthContributor is the common contract that lets Actuator build the health tree. A HealthIndicator is a leaf contributor that computes a Health result, normally containing a Status and optional detail data. Reactive applications can also use reactive health contributors, but the resulting endpoint model is still a tree of contributors that Actuator aggregates.

A typical custom HealthIndicator evaluates one bounded operational condition and returns UP when the condition is usable or another status when it is not. Detail fields can explain evidence such as a queue depth or last successful check, but they should not contain secrets or large diagnostic payloads.

The important design point is responsibility. A health indicator should report evidence about a component; it should not attempt to repair the component, restart the application, or encode an entire monitoring policy.

</details>

- [Back to top](#back-to-top)

---

## <a id="composite-health-contributors">How Do Composite Health Contributors Form a Health Tree?</a>

<details>
<summary>Click for details</summary>

A CompositeHealthContributor groups child contributors under one node, producing a hierarchy rather than a flat set of independent checks. This is useful when one logical subsystem contains several independently reportable parts, such as multiple data sources or several remote dependencies.

The health endpoint can expose the aggregate and, when component visibility permits, let callers navigate into component and nested-component paths. The tree shape helps an operator distinguish “system health is DOWN” from “which contributor caused the aggregate to become DOWN”.

Do not create deep health trees merely to mirror the application's package structure. Group contributors when the hierarchy carries operational meaning. The management view should help incident reasoning rather than become another inventory of internal implementation classes.

</details>

- [Back to top](#back-to-top)

---

## <a id="health-status-aggregation">How Is the Overall Health Status Aggregated?</a>

<details>
<summary>Click for details</summary>

Each leaf contributor returns a Status. Actuator's StatusAggregator combines the statuses from a set of contributors into the status of their parent or of the overall health endpoint. Boot supplies a default ordering where severe states such as DOWN and OUT_OF_SERVICE outrank UP, and applications can customize the order when they introduce additional status values.

For HTTP exposure, HttpCodeStatusMapper translates aggregate health status into a response status code. By default, DOWN and OUT_OF_SERVICE map to 503 Service Unavailable while UP and UNKNOWN remain 200 responses. If any custom `management.endpoint.health.status.http-mapping.*` entry is configured, Boot stops applying the default DOWN and OUT_OF_SERVICE mappings. Retain those 503 mappings explicitly alongside custom statuses when that behavior is still required.

Aggregation means one failing contributor can influence the system result, so indicator selection matters. A check that is noisy, slow, or tied to a nonessential external system can make the overall health view less trustworthy even when the application is otherwise useful.

</details>

- [Back to top](#back-to-top)

---

## <a id="health-detail-visibility">How Should Health Components and Details Be Exposed?</a>

<details>
<summary>Click for details</summary>

Health status and health detail visibility are separate concerns. management.endpoint.health.show-details and management.endpoint.health.show-components control how much of the contributor tree is returned. Boot supports never, when-authorized, and always policies, with never as the conservative default for details.

When using when-authorized, management.endpoint.health.roles can define which roles count as authorized for health details. The actual authentication and role assignment still come from the application's security configuration. Actuator only applies the configured visibility rule to its health representation.

Use the least detail needed by each audience. A load balancer usually needs only a status. An operator may need component names and diagnostic fields. Avoid placing credentials, tokens, full connection strings, or large exception dumps into health details even when the endpoint is secured.

</details>

- [Back to top](#back-to-top)

---

## <a id="custom-health-indicators">When Should You Add a Custom Health Indicator?</a>

<details>
<summary>Click for details</summary>

Add a custom HealthIndicator when the application has an operational condition that materially changes whether it should be considered healthy and that condition is not already represented by an auto-configured contributor. Examples include a required internal queue that has entered an unrecoverable state or a domain resource whose failure prevents the service from doing its job.

Keep the check fast, bounded, and side-effect free. Health endpoints may be called frequently by platforms and monitoring systems, so a health indicator should not perform expensive repair work or create new load that can worsen an incident. If a check calls a remote dependency, use timeouts and decide whether that dependency truly belongs in the overall group or only a specialized health group.

Custom health is an operational signal, not a business rule API. If callers need rich domain behavior or workflow status, that contract usually belongs in the business surface rather than a HealthIndicator.

</details>

- [Back to top](#back-to-top)
