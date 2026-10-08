<a id="back-to-top"></a>

# Metrics Endpoint and the Micrometer Boundary

## Menu
- [How Does Boot Integrate Micrometer MeterRegistry Infrastructure?](#micrometer-meter-registry-boundary)
- [What Is the Actuator Metrics Endpoint For?](#metrics-endpoint-purpose)
- [How Do Meter Names and Measurements Appear in the Metrics Endpoint?](#meter-name-measurements)
- [How Can Tags Narrow a Metrics Inspection?](#tag-filtered-metrics)
- [How Is Inspecting Metrics Different from Exporting Them?](#metrics-export-boundary)
- [Which Metrics Design and Backend Concerns Belong Outside Actuator?](#metric-design-boundary)

## <a id="micrometer-meter-registry-boundary">How Does Boot Integrate Micrometer MeterRegistry Infrastructure?</a>

<details>
<summary>Click for details</summary>

Spring Boot Actuator integrates Micrometer as the metrics facade used by Boot's metrics auto-configuration. When supported registry implementations are present, Boot creates and configures MeterRegistry infrastructure and binds many framework, JVM, process, and technology-specific meters automatically. Application code can also register meters through the Spring-managed registry.

This is the boundary to remember: Micrometer owns the meter model and registry APIs; Spring Boot owns the auto-configuration that assembles those pieces for a Boot application; Actuator provides management endpoints that inspect or expose selected results.

The presence of Actuator therefore does not mean every metric is an Actuator invention. Many meters come from Micrometer binders or framework instrumentation. The Actuator module teaches how Boot integrates and surfaces them, while deeper meter design remains with Micrometer/observability.

### References

- [Spring Boot 3.3 — Metrics](https://docs.spring.io/spring-boot/3.3/reference/actuator/metrics.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="metrics-endpoint-purpose">What Is the Actuator Metrics Endpoint For?</a>

<details>
<summary>Click for details</summary>

The metrics endpoint is a diagnostic inspection surface over meters currently known to the application's MeterRegistry. The collection at /actuator/metrics lists available meter names, and /actuator/metrics/{name} drills into one meter name when the endpoint is exposed.

This endpoint is useful during development and incidents because it answers questions such as “is this meter registered?”, “what measurements are currently available?”, and “which tag values exist?”. It is not designed as a long-term metrics transport or time-series query engine.

Because Boot 3.3 exposes only health remotely by default, metrics must be explicitly included in the desired exposure technology before a remote caller can inspect it. That requirement is separate from the fact that the metrics endpoint itself is enabled.

</details>

- [Back to top](#back-to-top)

---

## <a id="meter-name-measurements">How Do Meter Names and Measurements Appear in the Metrics Endpoint?</a>

<details>
<summary>Click for details</summary>

Metrics endpoint selectors use the meter name as it exists in the application's Micrometer model. A monitoring backend may normalize that name for its own naming convention, but the Actuator selector still uses the original application meter name. For example, a backend may render a dotted name differently while /actuator/metrics expects the dotted Micrometer name.

The endpoint returns measurements associated with matching meters, such as COUNT, TOTAL_TIME, VALUE, or other statistics appropriate to the meter type. When several meters share the same name but differ by tags, the returned measurements can represent an aggregate across the matching series.

The response also reports available tag dimensions and values that can be used for a narrower query. Reading the endpoint therefore requires understanding that one meter name can represent multiple tagged meter identities.

</details>

- [Back to top](#back-to-top)

---

## <a id="tag-filtered-metrics">How Can Tags Narrow a Metrics Inspection?</a>

<details>
<summary>Click for details</summary>

The metrics endpoint accepts tag=KEY:VALUE query parameters to restrict a meter inspection to matching tag dimensions. Multiple tag parameters can narrow the selection further. This is useful when one meter name represents several regions, URI patterns, outcomes, memory areas, or other dimensions.

Filtering does not create a new metric; it selects from the meter identities already registered. If several identities still match, Actuator aggregates the reported measurements across those matches. Available-tags information helps the caller discover which dimensions can be supplied.

Use tag filtering for diagnosis, not as a substitute for good metric cardinality design. A meter with unbounded user IDs, request IDs, or other high-cardinality values remains an observability design problem even if the Actuator endpoint can filter it.

</details>

- [Back to top](#back-to-top)

---

## <a id="metrics-export-boundary">How Is Inspecting Metrics Different from Exporting Them?</a>

<details>
<summary>Click for details</summary>

Inspecting /actuator/metrics and exporting metrics to a monitoring system are different flows. The metrics endpoint answers an on-demand management request. A metrics registry/exporter integrates with a backend and publishes or exposes telemetry in the format and cadence that backend expects.

Prometheus illustrates the distinction. With the Prometheus registry dependency, Boot can expose a dedicated prometheus endpoint containing scrape-format data. That endpoint is designed for a Prometheus server, while the generic metrics endpoint is designed for diagnostic exploration of Micrometer meters.

Other registries may push data or expose it through their own mechanisms. Actuator's role is to integrate the Boot application with those registries and endpoint surfaces; retention, querying, dashboards, alerting, and backend availability belong to external observability systems.

</details>

- [Back to top](#back-to-top)

---

## <a id="metric-design-boundary">Which Metrics Design and Backend Concerns Belong Outside Actuator?</a>

<details>
<summary>Click for details</summary>

Actuator can show what meters exist, but it does not decide what an application's metric model should be. Naming conventions, low-cardinality dimensions, counters versus gauges versus timers, histogram/SLO configuration, and the business meaning of a measurement are Micrometer/observability design concerns.

Poor meter design can create backend cost or misleading dashboards even when Boot configuration is technically correct. In particular, unbounded tags can create a rapidly growing number of time series. Actuator may help reveal the symptom by listing tag values, but the fix belongs to instrumentation design.

Similarly, backend-specific queries, aggregation across instances, alert thresholds, recording rules, dashboards, retention, and capacity planning are outside this module. The learner should leave this chapter knowing where Boot's integration ends.

</details>

- [Back to top](#back-to-top)
