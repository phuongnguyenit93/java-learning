<a id="back-to-top"></a>

# Graceful shutdown

## Menu
- [Immediate and Graceful Server Shutdown](#server-shutdown-modes)
- [Where Graceful Shutdown Fits in ApplicationContext Closing](#graceful-shutdown-lifecycle)
- [Configuring the Shutdown Phase Timeout](#shutdown-phase-timeout)
- [How Supported Servers Stop Accepting New Work](#server-specific-shutdown-behavior)
- [The Grace Period for In-flight Requests](#in-flight-request-grace-period)
- [Handoff to Application Runtime and Availability Concerns](#shutdown-runtime-handoff)

## <a id="server-shutdown-modes">Immediate and Graceful Server Shutdown</a>

<details>
<summary>Click for details</summary>
Boot 3.3 supports `immediate` and `graceful` web-server shutdown. The 3.3 default is `immediate`. Enable a grace period explicitly with:

```properties
server.shutdown=graceful
```

Graceful shutdown is supported across the four embedded server families used by Boot 3.3 and for both Servlet and reactive applications. It changes how the server stops accepting new work while the application context is closing.

### References

- [Spring Boot 3.3 Reference — Graceful Shutdown](https://docs.spring.io/spring-boot/3.3/reference/web/graceful-shutdown.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="graceful-shutdown-lifecycle">Where Graceful Shutdown Fits in ApplicationContext Closing</a>

<details>
<summary>Click for details</summary>
Graceful server shutdown happens as part of closing the `ApplicationContext`. Boot performs it in the earliest phase of stopping `SmartLifecycle` beans so the web entry point begins refusing new work while the rest of the application proceeds through its coordinated shutdown lifecycle.

That ordering is why graceful shutdown is a web-runtime topic with an application-runtime handoff. The server participates in the same context closure rather than running an unrelated shutdown process outside Spring.

</details>

- [Back to top](#back-to-top)

---

## <a id="shutdown-phase-timeout">Configuring the Shutdown Phase Timeout</a>

<details>
<summary>Click for details</summary>
`spring.lifecycle.timeout-per-shutdown-phase` controls the time budget for a shutdown phase. During graceful web-server shutdown, that budget is the grace period in which existing requests are allowed to finish.

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
```

This property belongs to the wider application lifecycle, so changing it can affect other `SmartLifecycle` participants in the same phase. Choose it as an application shutdown budget, not as an isolated HTTP timeout.

</details>

- [Back to top](#back-to-top)

---

## <a id="server-specific-shutdown-behavior">How Supported Servers Stop Accepting New Work</a>

<details>
<summary>Click for details</summary>
The common Boot contract is "finish in-flight work while refusing new work", but the mechanism differs by server. In Boot 3.3, Jetty, Reactor Netty, and Tomcat stop accepting new requests at the network layer during graceful shutdown.

Undertow behaves differently: it can continue accepting connections but responds to new requests with HTTP `503 Service Unavailable`. Persistent connections can also affect what a client observes, so do not use one server's wire behavior as the definition of graceful shutdown.

</details>

- [Back to top](#back-to-top)

---

## <a id="in-flight-request-grace-period">The Grace Period for In-flight Requests</a>

<details>
<summary>Click for details</summary>
Requests that were already being processed receive an opportunity to complete during the configured shutdown phase. Graceful shutdown therefore reduces avoidable failures during planned termination, rolling deployment, or instance replacement.

It is still bounded shutdown, not an unlimited wait. Application work must respect the surrounding lifecycle budget, and external systems such as orchestrators or load balancers must provide enough termination time for the process to use that grace period.

</details>

- [Back to top](#back-to-top)

---

## <a id="shutdown-runtime-handoff">Handoff to Application Runtime and Availability Concerns</a>

<details>
<summary>Click for details</summary>
The web-runtime responsibility ends once the server's graceful-stop behavior and its lifecycle timing are clear. Application availability state, process signals, other lifecycle beans, background work, and orchestrator termination policy belong to the broader application-runtime and infrastructure owners.

One practical boundary is the termination signal itself. Boot's documentation notes that IDE stop actions may be immediate when the IDE does not send an appropriate `SIGTERM`. Graceful shutdown can only participate when the process actually enters the normal context-closing path.

</details>

- [Back to top](#back-to-top)
