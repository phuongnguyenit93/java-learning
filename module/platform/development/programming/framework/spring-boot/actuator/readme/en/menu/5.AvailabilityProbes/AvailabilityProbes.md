<a id="back-to-top"></a>

# Liveness, Readiness, and Health Probes

## Menu
- [How Does Actuator Consume Boot Application Availability?](#availability-health-handoff)
- [How Do Liveness and Readiness Become Health Groups?](#liveness-readiness-health-groups)
- [What Operational Views Do Liveness and Readiness Probe Endpoints Provide?](#probe-endpoint-paths)
- [Why Should Probe Dependency Checks Be Chosen Carefully?](#probe-external-dependency-boundary)
- [Where Does Actuator Hand Availability-State Ownership Back to Application Runtime?](#application-runtime-availability-ownership)

## <a id="availability-health-handoff">How Does Actuator Consume Boot Application Availability?</a>

<details>
<summary>Click for details</summary>

Spring Boot's application-runtime model owns ApplicationAvailability and the transitions of LivenessState and ReadinessState. Actuator consumes those states through dedicated health indicators so management clients can observe them without becoming part of the lifecycle mechanism that produces them.

LivenessStateHealthIndicator converts the current liveness state into health status, while ReadinessStateHealthIndicator does the same for readiness. The result joins the normal HealthContributor model, which means availability can be represented through the health endpoint and grouped like other health contributors.

This handoff is intentionally one-way from state ownership to management view. A probe request reads the state that application runtime has reached; the probe endpoint does not decide when runners finish, when readiness changes, or when shutdown begins.

### References

- [Spring Boot 3.3 — Kubernetes Probes](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="liveness-readiness-health-groups">How Do Liveness and Readiness Become Health Groups?</a>

<details>
<summary>Click for details</summary>

Actuator models liveness and readiness as health groups. When probe support is active, the groups are named liveness and readiness and are backed by the corresponding availability-state health indicators. In Kubernetes environments Boot enables the probe groups automatically; they can also be enabled explicitly with management.endpoint.health.probes.enabled.

Because these are health groups, the normal health-group configuration model applies. An application can control included contributors and group-level detail/status settings. Boot intentionally does not add arbitrary external health indicators to the readiness or liveness groups by default.

Treat the groups as operational contracts with different meanings. Liveness answers whether restarting the process may be appropriate. Readiness answers whether traffic should currently be routed to the process. Giving both groups the same dependency checks defeats that distinction.

</details>

- [Back to top](#back-to-top)

---

## <a id="probe-endpoint-paths">What Operational Views Do Liveness and Readiness Probe Endpoints Provide?</a>

<details>
<summary>Click for details</summary>

With the default management web base path, the liveness and readiness health groups are available at /actuator/health/liveness and /actuator/health/readiness when the probe groups are enabled and health is exposed. Deployment platforms can call those paths as HTTP probes.

If Actuator runs on a separate management port, a probe can remain healthy even when the main application port or its web infrastructure cannot accept requests. Boot therefore supports management.endpoint.health.probes.add-additional-paths so liveness and readiness can also be exposed on the main server as /livez and /readyz.

The path choice should test the failure domain that the platform actually cares about. A dedicated management port is useful for isolation, but it should not accidentally hide failure of the main request path from a readiness probe.

</details>

- [Back to top](#back-to-top)

---

## <a id="probe-external-dependency-boundary">Why Should Probe Dependency Checks Be Chosen Carefully?</a>

<details>
<summary>Click for details</summary>

Liveness should not normally depend on external systems such as databases, remote APIs, or caches. If a shared database fails and every instance reports broken liveness, an orchestrator may restart all instances and create a cascading failure even though restarting cannot repair the database.

Readiness can include external conditions when losing that dependency means the instance should stop receiving traffic, but the decision is application-specific. Boot deliberately does not insert extra external health indicators into the readiness group by default. Teams must weigh whether removing all instances from service is better than serving degraded behavior.

If a dependency check is included, make it bounded and reliable. Timeouts, failure semantics, and the effect on load balancing matter more than simply reusing every HealthIndicator in every probe group.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-runtime-availability-ownership">Where Does Actuator Hand Availability-State Ownership Back to Application Runtime?</a>

<details>
<summary>Click for details</summary>

Actuator owns the health representation of availability. application-runtime owns the underlying states and lifecycle timing. The distinction becomes visible during startup: liveness can become CORRECT before readiness becomes ACCEPTING_TRAFFIC, because startup runners still need to complete before the application is ready.

During shutdown, application-runtime changes readiness as part of the lifecycle, and Actuator reflects the resulting state. If a probe shows REFUSING_TRAFFIC at an unexpected time, first determine why the runtime state changed. Changing the health group only changes what is exposed; it does not repair the state transition that produced the signal.

For lifecycle events, runners, AvailabilityChangeEvent, or manual state publication, return to application-runtime. For health groups, probe paths, health detail visibility, and how those states are presented to management clients, remain in Actuator.

</details>

- [Back to top](#back-to-top)
