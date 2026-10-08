<a id="back-to-top"></a>

# Lazy initialization and startup optimization

## Menu
- [What Does Lazy Initialization Change at Startup?](#lazy-initialization-purpose)
- [Which Failures and Costs Can Lazy Initialization Defer?](#lazy-initialization-tradeoffs)
- [Why Measure Startup Before Optimizing It?](#startup-measurement)
- [How Does `ApplicationStartup` Provide Startup Evidence?](#application-startup-tracking)
- [Where Does Startup Tracking Hand Off to Actuator and Observability?](#startup-observability-boundary)

## <a id="lazy-initialization-purpose">What Does Lazy Initialization Change at Startup?</a>

<details>
<summary>Click for details</summary>

By default, a Boot application creates many singleton beans during context startup. Lazy initialization changes that timing: eligible beans are created when they are first needed instead of eagerly during startup. Boot exposes this as `spring.main.lazy-initialization=true` and through the `SpringApplication`/builder API.

That can reduce the amount of work on the critical startup path, especially when parts of the bean graph are not needed immediately. It does not remove the work; it moves some work to first use.

The right mental model is therefore *deferred initialization*, not free startup performance. A fast "ready" process that performs expensive first-request initialization may simply have moved latency from deployment time to runtime traffic.

</details>

- [Back to top](#back-to-top)

---

## <a id="lazy-initialization-tradeoffs">Which Failures and Costs Can Lazy Initialization Defer?</a>

<details>
<summary>Click for details</summary>

The largest cost of lazy initialization is delayed failure discovery. A misconfigured bean that would normally fail while the application starts may now fail only when a request or background task first needs it. That changes both the timing and operational impact of the error.

Lazy initialization also does not mean the JVM only needs memory for the beans created at startup. The application may eventually create the full graph, so capacity planning must consider the steady-state object set.

Spring Boot therefore leaves lazy initialization disabled by default. Enable it for a measured reason, then decide which beans should remain eager with `@Lazy(false)` when early validation is more valuable than deferred creation.

### References

- [Spring Boot 3.3 — Lazy Initialization](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.lazy-initialization)

</details>

- [Back to top](#back-to-top)

---

## <a id="startup-measurement">Why Measure Startup Before Optimizing It?</a>

<details>
<summary>Click for details</summary>

Startup optimization should begin with a phase and a measurement, not a property. "Startup takes 12 seconds" is an observation; the useful question is which work consumes that time and whether it belongs on the startup critical path.

Possible causes live in different ownership domains: bean initialization, external network calls in startup code, condition-heavy application configuration, logging setup, a runner, or a web-server concern. Tuning lazy initialization cannot fix all of them.

Establish a repeatable baseline, compare like-for-like runs, then change one mechanism whose cost you can explain. The goal is not the smallest startup number at any price; it is acceptable startup time while preserving early failure detection and predictable first-use latency.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-startup-tracking">How Does `ApplicationStartup` Provide Startup Evidence?</a>

<details>
<summary>Click for details</summary>

Spring Framework's `ApplicationStartup` abstraction lets startup code record structured `StartupStep` data. Spring Boot can be configured with an implementation such as `BufferingApplicationStartup` so startup activity can be captured for later inspection.

```java
SpringApplication application = new SpringApplication(MyApplication.class);
application.setApplicationStartup(new BufferingApplicationStartup(2048));
application.run(args);
```

This changes the investigation from guessing about "Spring being slow" to observing named startup steps and their timing. The data still needs interpretation: a slow step can point to a bean or framework activity, but the fix belongs to the component actually doing the work.

Keep the instrumentation cost appropriate to the environment and use the captured evidence to form a specific optimization hypothesis.

</details>

- [Back to top](#back-to-top)

---

## <a id="startup-observability-boundary">Where Does Startup Tracking Hand Off to Actuator and Observability?</a>

<details>
<summary>Click for details</summary>

This module owns the use of startup tracking as runtime evidence and the decision model around lazy initialization. Exposing buffered startup information through production endpoints is an Actuator concern, and broader tracing/metrics pipelines belong to observability curricula.

That split keeps the learning path clean: first understand what startup measurement tells you about Boot lifecycle cost; then learn how production tooling exposes or transports that information.

Similarly, AOT/native-image startup characteristics belong to the dedicated `native-image` module. Do not use normal JVM startup tuning rules as a substitute for understanding a different runtime form. Classify the runtime and measurement source before comparing results.

</details>

- [Back to top](#back-to-top)
