<a id="back-to-top"></a>

# Availability states, failure analysis, and exit handling

## Menu
- [What Do Liveness and Readiness Mean in Boot Runtime?](#liveness-readiness-model)
- [When Do Availability States Change During Startup?](#availability-state-transitions)
- [How Does Readiness Change as Shutdown Begins?](#availability-shutdown-transition)
- [How Do `ApplicationAvailability` and `AvailabilityChangeEvent` Expose Runtime State?](#availability-api)
- [How Does Boot Turn Startup Failure into Actionable Diagnostics?](#startup-failure-analysis)
- [What Does SpringApplication's Shutdown Hook Own?](#shutdown-hook)
- [How Do `ExitCodeGenerator` and `SpringApplication.exit` Communicate Process Status?](#application-exit-codes)
- [Where Do Generic Runtime Shutdown and Web-Server Graceful Shutdown Split?](#shutdown-boundaries)

## <a id="liveness-readiness-model">What Do Liveness and Readiness Mean in Boot Runtime?</a>

<details>
<summary>Click for details</summary>

Liveness and readiness answer different operational questions. Liveness asks whether the application's internal state is healthy enough to keep running or recover by itself. Readiness asks whether the application should currently receive traffic.

That difference changes how dependencies should influence each state. A temporary database outage may make some requests impossible, but using that external outage as a liveness failure can cause the platform to restart every application instance and amplify the incident. Readiness can be more conservative because refusing new traffic does not necessarily kill the process.

Boot owns these availability states as runtime signals. Actuator can expose them as health groups, but endpoint exposure and probe configuration belong to the Actuator module.

</details>

- [Back to top](#back-to-top)

---

## <a id="availability-state-transitions">When Do Availability States Change During Startup?</a>

<details>
<summary>Click for details</summary>

Boot aligns default availability with the startup timeline. After the context has refreshed and `ApplicationStartedEvent` is published, it emits a liveness change to `LivenessState.CORRECT`. Runners still execute after that point. Once runners complete, Boot publishes `ApplicationReadyEvent` and then changes readiness to `ReadinessState.ACCEPTING_TRAFFIC`.

```text
context refreshed
  -> started
  -> LIVE / CORRECT
  -> runners
  -> ready
  -> READY / ACCEPTING_TRAFFIC
```

This sequence explains why "live" and "ready" are not synonyms. The process can have a valid context while required startup work is still running. When troubleshooting a slow deployment, inspect where the application is on this timeline before changing probe settings.

</details>

- [Back to top](#back-to-top)

---

## <a id="availability-shutdown-transition">How Does Readiness Change as Shutdown Begins?</a>

<details>
<summary>Click for details</summary>

Availability is also useful when an application leaves steady state. Before or during shutdown, readiness should move away from accepting new traffic so infrastructure can stop routing new work while existing lifecycle shutdown proceeds.

At the generic Boot level, think in terms of runtime state transitions and context shutdown. The exact draining behavior of an embedded HTTP server—how it stops accepting requests and how long existing requests receive—is owned by `web-runtime` and its graceful-shutdown configuration.

This boundary prevents two different problems from being conflated: *declaring that the application should no longer receive traffic* and *implementing protocol/server-specific request draining*. They cooperate, but they are not the same mechanism.

</details>

- [Back to top](#back-to-top)

---

## <a id="availability-api">How Do `ApplicationAvailability` and `AvailabilityChangeEvent` Expose Runtime State?</a>

<details>
<summary>Click for details</summary>

`ApplicationAvailability` is the read side of Boot's availability model. Application code can inject it and ask for the current liveness or readiness state. `AvailabilityChangeEvent` is the change/observation side: components can listen for state transitions, and application code can publish a new state when it has enough domain evidence to do so.

```java
AvailabilityChangeEvent.publish(
        eventPublisher,
        this,
        ReadinessState.REFUSING_TRAFFIC);
```

Publish state changes intentionally. A short external hiccup should not automatically become `LivenessState.BROKEN`; Boot's guidance is to keep liveness focused on internal unrecoverable state. The API communicates application state—it does not decide your operational policy for you.

### References

- [Spring Boot 3.3 — Application Availability](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.application-availability)

</details>

- [Back to top](#back-to-top)

---

## <a id="startup-failure-analysis">How Does Boot Turn Startup Failure into Actionable Diagnostics?</a>

<details>
<summary>Click for details</summary>

When startup fails, Boot first preserves the exception path and then gives registered `FailureAnalyzer` implementations a chance to turn known failures into a concise description plus a concrete action. The familiar `APPLICATION FAILED TO START` block is therefore a diagnostic layer over the underlying exception, not a substitute for it.

If no analyzer explains the failure, or if auto-configuration decisions are part of the question, enable Boot's debug output or the `ConditionEvaluationReportLoggingListener` to inspect the conditions report. Detailed condition matching belongs to the auto-configuration module; this chapter uses the report only as runtime failure evidence.

A good investigation order is: identify the root exception, read any failure analysis, then inspect configuration/condition evidence relevant to that failure. Avoid randomly changing properties until startup succeeds.

### References

- [Spring Boot 3.3 — Startup Failure](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.startup-failure)

</details>

- [Back to top](#back-to-top)

---

## <a id="shutdown-hook">What Does SpringApplication's Shutdown Hook Own?</a>

<details>
<summary>Click for details</summary>

`SpringApplication` registers a JVM shutdown hook by default so the `ApplicationContext` closes gracefully when the JVM exits normally. Closing the context allows Spring-managed destruction callbacks and lifecycle components to participate in shutdown rather than abruptly abandoning managed resources.

The hook is generic application-runtime behavior. It does not define how every external resource or protocol drains work. Individual technologies may have their own lifecycle integration, and embedded web-server graceful shutdown is taught in `web-runtime`.

The practical lesson is to keep long-lived resources inside managed lifecycles when possible. If application code creates unmanaged threads or resources outside the context, the presence of Boot's shutdown hook does not automatically make those resources orderly.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-exit-codes">How Do `ExitCodeGenerator` and `SpringApplication.exit` Communicate Process Status?</a>

<details>
<summary>Click for details</summary>

Some applications need to communicate a process result to the operating system or an orchestrating script. Boot supports `ExitCodeGenerator` beans and `ExitCodeExceptionMapper` so application-specific outcomes can be translated into an exit code. `SpringApplication.exit(context)` collects the generators and returns the resulting code.

Returning a code from `SpringApplication.exit` does not itself terminate the JVM; command-style applications commonly pass it to `System.exit(...)` when process termination is intended.

This is especially useful for batch or command applications where "completed with a domain failure" must be machine-readable. Keep exit-code mapping small and documented; it is an integration contract with the process caller, not a replacement for exception handling or application logs.

</details>

- [Back to top](#back-to-top)

---

## <a id="shutdown-boundaries">Where Do Generic Runtime Shutdown and Web-Server Graceful Shutdown Split?</a>

<details>
<summary>Click for details</summary>

Generic runtime shutdown owns the process/context story: readiness can stop accepting work, the JVM shutdown hook closes the `ApplicationContext`, managed bean lifecycles run, and the process can return an exit code. That model applies whether the Boot application is web, command-line, or another shape.

Web-server graceful shutdown adds a protocol-specific layer: the embedded server must stop accepting new requests and allow in-flight requests a defined opportunity to finish. Those server choices, timeouts, and supported server behavior belong to `web-runtime`.

When debugging shutdown, separate the layers. A context that never closes is an application-runtime problem; a server that drains HTTP requests differently than expected is a web-runtime problem; an unmanaged thread that keeps the JVM alive is a Java/application lifecycle problem.

</details>

- [Back to top](#back-to-top)
