<a id="back-to-top"></a>

# Runtime customization boundaries

## Menu
- [When Is Runtime Customization Actually Necessary?](#runtime-customization-decision)
- [Why Prefer Supported Configuration Properties First?](#property-first-customization)
- [What Can Be Customized Through `SpringApplication`?](#springapplication-customization)
- [When Does `SpringApplicationBuilder` Help?](#springapplicationbuilder-customization)
- [How Do You Choose a Runtime Extension Point by Lifecycle Timing?](#runtime-extension-point-selection)
- [Which Customization Belongs to Configuration, Auto-Configuration, the Container, or Web Runtime Instead?](#customization-ownership-boundaries)

## <a id="runtime-customization-decision">When Is Runtime Customization Actually Necessary?</a>

<details>
<summary>Click for details</summary>

Runtime customization is justified when the application's required behavior differs from Boot's supported defaults and no simpler configuration surface expresses that requirement. The decision should start from a concrete behavior—startup mode, listener registration, application shape, initialization policy—not from a desire to "take control" of Boot.

Every programmatic customization moves part of the runtime contract into application code. That can be appropriate, but it is harder to discover than a standard property and may interact with auto-configuration or framework conventions.

Before customizing, identify the owning layer and lifecycle phase. Many apparent SpringApplication problems are actually externalized configuration, auto-configuration, container configuration, executor configuration, or web-server configuration. Customize at the narrowest owner that truly controls the behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="property-first-customization">Why Prefer Supported Configuration Properties First?</a>

<details>
<summary>Click for details</summary>

Boot configuration properties are the preferred first option when they already represent the desired behavior. Properties are visible in configuration metadata, participate in Boot's externalized-configuration model, and usually preserve auto-configuration's intended back-off and lifecycle behavior.

Programmatic customization is appropriate when the property surface cannot express the requirement, or when the application must assemble `SpringApplication` itself before the normal context exists. Do not recreate property binding in `main()` merely to make configuration look explicit.

This is also an ownership boundary: property precedence, profiles, and `@ConfigurationProperties` mechanics are taught in `externalized-configuration`. Here the concern is choosing the supported runtime surface before reaching for lower-level hooks.

</details>

- [Back to top](#back-to-top)

---

## <a id="springapplication-customization">What Can Be Customized Through `SpringApplication`?</a>

<details>
<summary>Click for details</summary>

Instead of calling the static `SpringApplication.run(...)` shortcut, an application can create a `SpringApplication` instance, change supported settings, and then call `run(args)`. This is useful for options that must exist at bootstrap time, such as banner mode, listeners, initializers, application type, lazy initialization, or other settings exposed by the `SpringApplication` API.

```java
SpringApplication app = new SpringApplication(MyApplication.class);
app.setBannerMode(Banner.Mode.OFF);
app.addListeners(new BootstrapListener());
app.run(args);
```

Keep the bootstrap code declarative and small. If customization starts registering ordinary business services or reproducing container configuration, the code has crossed from Boot runtime setup into responsibilities better expressed by Spring configuration or auto-configuration.

### References

- [Spring Boot 3.3 — Customizing SpringApplication](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.customizing-spring-application)

</details>

- [Back to top](#back-to-top)

---

## <a id="springapplicationbuilder-customization">When Does `SpringApplicationBuilder` Help?</a>

<details>
<summary>Click for details</summary>

`SpringApplicationBuilder` offers a fluent way to configure and run a `SpringApplication`. Its distinctive use case is building an `ApplicationContext` hierarchy with parent/child relationships, although it can also make ordinary bootstrap options easier to compose.

Hierarchies introduce constraints: parent and child contexts share an `Environment`, and web components must be placed in the child context according to Boot's documented restrictions. A hierarchy is therefore an architectural choice, not a stylistic alternative to one application context.

Use the builder when the hierarchy or fluent assembly itself solves a real requirement. For a normal single-context application, the static `run` method or a small customized `SpringApplication` is usually easier to understand.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-extension-point-selection">How Do You Choose a Runtime Extension Point by Lifecycle Timing?</a>

<details>
<summary>Click for details</summary>

Choose runtime extension points by the earliest phase that satisfies the work's prerequisites and by the work's intent.

| Need | Prefer |
| --- | --- |
| Observe a lifecycle transition | `ApplicationListener` / event listener |
| Observe an event before beans exist | early listener registration |
| Use normal beans for required startup work | `ApplicationRunner` / `CommandLineRunner` |
| Run ongoing background work | managed executor or scheduler |
| Change supported Boot bootstrap settings | property, `SpringApplication`, or builder |

Picking the earliest possible hook is rarely a goal. Earlier phases have fewer guarantees and make dependencies harder to express. Pick the *latest safe* phase that still meets the requirement so the application can use normal managed infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="customization-ownership-boundaries">Which Customization Belongs to Configuration, Auto-Configuration, the Container, or Web Runtime Instead?</a>

<details>
<summary>Click for details</summary>

A runtime customization can be technically possible in several layers, but ownership determines which choice remains understandable. Configuration values and profiles belong to `externalized-configuration`. Conditional bean creation, back-off rules, and reusable application defaults belong to `auto-configuration`. Generic bean lifecycle and context internals belong to Spring Framework.

Boot web-server factories, server properties, connectors, TLS consumption, forwarded headers, and graceful shutdown belong to `web-runtime`. Production diagnostic exposure belongs to Actuator. Build/package behavior belongs to `build-tooling-packaging`.

This module keeps only cross-cutting `SpringApplication` runtime customization and the decision model for choosing hooks. If a customization can be named more precisely by another owner, hand it off rather than growing an all-purpose "runtime customization" bucket.

</details>

- [Back to top](#back-to-top)
