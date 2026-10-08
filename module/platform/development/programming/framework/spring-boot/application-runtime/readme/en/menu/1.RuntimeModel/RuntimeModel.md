<a id="back-to-top"></a>

# Spring Boot application runtime model

## Menu
- [What Does Spring Boot Own in the Application Runtime?](#application-runtime-role)
- [Why Does Spring Boot Need a Runtime Coordination Layer?](#application-runtime-problem)
- [Which Inputs Shape the Runtime Before the Context Is Ready?](#runtime-inputs-and-context)
- [What Are the Major Phases from `SpringApplication.run` to Ready State?](#runtime-phase-model)
- [What Changes Once the Application Reaches Steady-State Execution?](#steady-state-runtime)
- [Where Does Application Runtime Hand Off to Neighboring Modules?](#runtime-ownership-handoffs)

## <a id="application-runtime-role">What Does Spring Boot Own in the Application Runtime?</a>

<details>
<summary>Click for details</summary>

Spring Boot application runtime is the coordination layer that turns a configured `SpringApplication` into a running process with a managed `ApplicationContext`. By this point the learner already knows the basic bootstrap model; here the focus shifts to *when* Boot performs work and *which* runtime hook owns it.

Boot coordinates the environment and context startup, publishes Boot lifecycle events, invokes startup runners, updates application availability, supplies selected runtime infrastructure through auto-configuration, and closes the context when the JVM shuts down. The application still contains ordinary Spring beans and Java code; Boot adds conventions and integration around them rather than replacing the Spring container or JVM.

That distinction is useful during debugging: first ask whether a symptom comes from Boot's runtime coordination, a Spring Framework mechanism, or application code. The remaining chapters refine that decision with concrete lifecycle phases and integrations.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-runtime-problem">Why Does Spring Boot Need a Runtime Coordination Layer?</a>

<details>
<summary>Click for details</summary>

A nontrivial application has work that must happen at different moments: configuration must exist before beans are created, some observers must see very early startup, initialization work may need to run after the context is refreshed, and traffic should not be accepted until required startup work has finished. Treating all of those moments as interchangeable callbacks creates ordering bugs and unclear failure behavior.

`SpringApplication` gives these moments a common timeline. Boot can therefore attach events, runners, availability changes, failure analysis, logging initialization, managed executors, and development services to known phases. The benefit is not more callbacks; it is a shared runtime vocabulary for placing work at the phase where its prerequisites are actually true.

This chapter supplies that vocabulary before later chapters examine individual hooks.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-inputs-and-context">Which Inputs Shape the Runtime Before the Context Is Ready?</a>

<details>
<summary>Click for details</summary>

Before application-specific startup work can run, the runtime has already been shaped by inputs owned by earlier Spring Boot modules. Command-line arguments participate in runtime input, externalized configuration prepares values and profiles, the classpath influences what auto-configuration can match, and auto-configuration contributes beans to the context.

The key runtime idea is dependency: events and hooks do not all see the same world. An early environment event can observe an `Environment` before an `ApplicationContext` exists. A later context event can observe registered beans. A runner executes after context refresh and can use ordinary application beans.

This module consumes those inputs but does not re-teach property-source precedence, binding, profiles, or condition evaluation. When diagnosing a runtime problem, identify the phase first, then return to `externalized-configuration` or `auto-configuration` if the root cause is actually how the context was shaped.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-phase-model">What Are the Major Phases from `SpringApplication.run` to Ready State?</a>

<details>
<summary>Click for details</summary>

A useful mental model is a sequence of increasingly complete runtime states:

```text
run starts
  -> Environment is prepared
  -> ApplicationContext is created and initialized
  -> bean definitions are loaded
  -> context refresh completes
  -> ApplicationStartedEvent
  -> liveness becomes CORRECT
  -> ApplicationRunner / CommandLineRunner execute
  -> ApplicationReadyEvent
  -> readiness becomes ACCEPTING_TRAFFIC
```

Boot also emits a failed event if startup aborts. Web applications can publish web-server-specific events between context preparation and the started phase, but server mechanics belong to `web-runtime`.

Use this timeline as a placement tool. If code needs beans, a pre-context hook is too early. If readiness must wait for initialization, a runner is a better fit than an event that fires before runners complete. Later chapters attach precise APIs to each stage.

### References

- [Spring Boot 3.3 — SpringApplication: Application Events and Listeners](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.application-events-and-listeners)

</details>

- [Back to top](#back-to-top)

---

## <a id="steady-state-runtime">What Changes Once the Application Reaches Steady-State Execution?</a>

<details>
<summary>Click for details</summary>

Reaching ready state changes the question from "how does the application start?" to "what runtime services and state does Boot continue to coordinate?" Background work can use Boot-managed task infrastructure, logging remains configured through Boot's integration layer, components can query or publish availability, and supported clients can consume named SSL bundles. During local development, Boot may also manage Docker Compose services.

Steady state does not mean startup concerns disappear. A bad executor choice can starve work, availability can change, logs can reveal runtime failures, and the process still needs a clean exit path. The runtime model therefore spans startup, normal execution, failure, and shutdown.

Boot provides integration points for these concerns; business scheduling policy, generic concurrency, log aggregation, TLS protocol design, and container operations remain outside this module.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-ownership-handoffs">Where Does Application Runtime Hand Off to Neighboring Modules?</a>

<details>
<summary>Click for details</summary>

The runtime model is most useful when it tells you where to stop. Configuration-source precedence and binding belong to `externalized-configuration`; conditional bean creation and custom auto-configuration belong to `auto-configuration`. Detailed web-server selection, server properties, server TLS, proxies, and graceful server shutdown belong to `web-runtime`.

`actuator` exposes production-oriented health and diagnostic endpoints that may consume runtime state, but this module owns the underlying Boot availability lifecycle. Java concurrency owns virtual-thread semantics; Spring Framework concurrency owns `@Async` and `@Scheduled` semantics. Observability/logging owns pipelines and backends; security/network curricula own TLS/PKI; containerization owns Docker and Compose mechanics.

Keeping these handoffs explicit lets this module explain Boot-specific runtime integration deeply without becoming a duplicate of every technology that Boot can connect to.

</details>

- [Back to top](#back-to-top)
