<a id="back-to-top"></a>

# Application lifecycle and events

## Menu
- [How Does the SpringApplication Event Timeline Progress?](#springapplication-event-timeline)
- [Which Events Happen Before an ApplicationContext Exists?](#pre-context-events)
- [How Do Context Refresh, Started, and Ready Differ?](#context-started-ready-events)
- [What Happens on the Failed Startup Path?](#failed-event-path)
- [Why Must Some Listeners Be Registered Before Bean Creation?](#early-listener-registration)
- [When Is an Application Event the Right Runtime Hook?](#event-hook-selection)

## <a id="springapplication-event-timeline">How Does the SpringApplication Event Timeline Progress?</a>

<details>
<summary>Click for details</summary>

SpringApplication events expose named checkpoints on the startup path. In Spring Boot 3.3 the important ordering is `ApplicationStartingEvent`, `ApplicationEnvironmentPreparedEvent`, `ApplicationContextInitializedEvent`, `ApplicationPreparedEvent`, `ApplicationStartedEvent`, a liveness `AvailabilityChangeEvent`, `ApplicationReadyEvent`, and then a readiness `AvailabilityChangeEvent`. `ApplicationFailedEvent` represents an exception on the startup path.

The order matters because each checkpoint has different guarantees. Before the context exists you cannot rely on beans. After refresh, normal context state is available. `ApplicationStartedEvent` still occurs before application and command-line runners, while `ApplicationReadyEvent` occurs after those runners.

Treat event names as lifecycle evidence, not merely notifications. If a listener needs information that does not exist yet at its chosen phase, moving the listener later is usually more correct than trying to reconstruct missing state.

### References

- [Spring Boot 3.3 — Application Events and Listeners](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.application-events-and-listeners)

</details>

- [Back to top](#back-to-top)

---

## <a id="pre-context-events">Which Events Happen Before an ApplicationContext Exists?</a>

<details>
<summary>Click for details</summary>

The earliest SpringApplication events exist specifically before a normal bean graph is available. `ApplicationStartingEvent` occurs near the beginning of `run`, `ApplicationEnvironmentPreparedEvent` occurs once the `Environment` is known but before the context is created, and `ApplicationContextInitializedEvent` occurs after context initializers have run but before bean definitions are loaded.

These phases are useful for infrastructure that truly needs early bootstrap visibility. They are a poor place for ordinary application services because dependency injection and application beans are not ready yet.

The design question is therefore not "which event can I listen to?" but "what state must this work observe?" If the work needs repositories, services, or other regular beans, wait for a later phase instead of forcing application behavior into bootstrap infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="context-started-ready-events">How Do Context Refresh, Started, and Ready Differ?</a>

<details>
<summary>Click for details</summary>

Three nearby milestones are easy to confuse. A refreshed context means bean definitions have been processed and the context refresh has completed. `ApplicationStartedEvent` comes after that refresh but *before* `ApplicationRunner` and `CommandLineRunner` components are called. `ApplicationReadyEvent` comes after those runners finish.

Boot connects availability to the same sequence: liveness becomes `CORRECT` after the started phase, while readiness becomes `ACCEPTING_TRAFFIC` after the ready event. This makes runner duration operationally meaningful: long runner work delays readiness even though the context is already live.

Choose the checkpoint based on the guarantee you need. "Context exists" is weaker than "startup runners finished". This distinction becomes the bridge from lifecycle events into the runners and availability chapters.

</details>

- [Back to top](#back-to-top)

---

## <a id="failed-event-path">What Happens on the Failed Startup Path?</a>

<details>
<summary>Click for details</summary>

Startup does not always reach `ApplicationReadyEvent`. If an exception escapes the startup process, Boot publishes `ApplicationFailedEvent`. This gives bootstrap-level listeners a final event containing the failed application context when available and the exception that ended startup.

The event is only one part of the failure path. Boot also allows `FailureAnalyzer` implementations to turn known failures into a focused description and action. When the problem involves auto-configuration conditions, the condition evaluation report is additional evidence rather than a replacement for the original exception.

Failure handling should preserve the root cause. A listener can add telemetry or cleanup, but it should not hide an exception merely to make startup appear successful. The availability/failure chapter goes deeper into diagnosis.

</details>

- [Back to top](#back-to-top)

---

## <a id="early-listener-registration">Why Must Some Listeners Be Registered Before Bean Creation?</a>

<details>
<summary>Click for details</summary>

A listener declared only as a normal `@Bean` cannot observe events that happen before the `ApplicationContext` has created that bean. Boot therefore supports registering early listeners directly on `SpringApplication` with `addListeners(...)`, through `SpringApplicationBuilder.listeners(...)`, or through the supported automatic listener registration mechanism.

This is a lifecycle constraint, not a dependency-injection trick. Register an early listener outside the bean lifecycle only when the event itself is early. Later events can usually use ordinary bean-based listeners, which keeps dependencies and testing simpler.

An early listener should also have a narrow responsibility. The earlier the hook, the fewer application services are safely available, so bootstrap listeners are best suited to bootstrap concerns rather than business initialization.

</details>

- [Back to top](#back-to-top)

---

## <a id="event-hook-selection">When Is an Application Event the Right Runtime Hook?</a>

<details>
<summary>Click for details</summary>

Use an application event when the work is fundamentally *observation or reaction to a lifecycle transition*. Examples include recording that the environment was prepared, reacting to the context being started, or observing readiness changes. Events are also useful when several independent listeners should react without the publisher knowing them directly.

Do not use a synchronous event listener for lengthy startup work merely because the event occurs at a convenient time. Spring application events are delivered in the same thread by default, so expensive work can extend or block the startup path. Work that must complete before readiness often fits a runner; ongoing work belongs on an executor or scheduler.

The right hook follows both timing and intent: event for lifecycle observation, runner for ordered startup work, managed executor/scheduler for background work, and configuration/customization APIs for changing Boot behavior.

</details>

- [Back to top](#back-to-top)
