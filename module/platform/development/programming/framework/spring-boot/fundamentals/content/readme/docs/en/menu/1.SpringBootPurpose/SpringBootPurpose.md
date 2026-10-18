<a id="back-to-top"></a>

# Spring Boot Purpose and Mental Model

## Menu
- [What Is Spring Boot and Why Does It Exist?](#spring-boot-purpose)
- [Spring Framework vs Spring Boot: What Does Each Own?](#boot-vs-framework)
- [Why Convention over Configuration Reduces Setup](#convention-over-configuration)
- [How Configuration Inputs Change a Boot Application](#externalized-configuration-orientation)
- [How the Main Spring Boot Capabilities Fit Together](#boot-capability-map)
- [What Fundamentals Owns and What Comes Next](#fundamentals-learning-boundary)

## <a id="spring-boot-purpose">What Is Spring Boot and Why Does It Exist?</a>

<details>
<summary>Click for details</summary>

Spring Boot is a layer around the Spring ecosystem that helps an application get from "I have some Spring-based code and dependencies" to "I have a runnable application" with less repeated setup. It does this through conventions, sensible defaults, dependency coordination, application bootstrap support, and integrations for common runtime shapes.

The important mental model is that Boot does not replace Spring Framework. Spring Framework still provides the container, dependency injection, configuration model, web frameworks, data-access abstractions, and many other core mechanisms. Boot makes common combinations of those mechanisms easier to assemble and start.

Without Boot, a team can still build a Spring application, but it usually has to make more setup decisions explicitly: which compatible dependency versions to use, how to bootstrap the application, which infrastructure beans are needed, how to package and run the result, and how to connect common libraries. Boot reduces that repeated work while leaving the choices observable and overridable.

By the end of Fundamentals, you should be able to explain a simple flow:

```text
application code + dependencies
        ↓
SpringApplication bootstrap
        ↓
Spring ApplicationContext
        ↓
Boot conventions and integrations shape the running application
```

### References

- [Spring Boot 3.3 — Developing with Spring Boot](https://docs.spring.io/spring-boot/3.3/reference/using/index.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-vs-framework">Spring Framework vs Spring Boot: What Does Each Own?</a>

<details>
<summary>Click for details</summary>

Spring Framework and Spring Boot solve different layers of the same application problem. Spring Framework supplies foundational application programming mechanisms such as `ApplicationContext`, bean registration, dependency injection, transactions, Spring MVC, and Spring WebFlux. Spring Boot supplies conventions and integration that make a Spring application easier to assemble, start, configure, observe during development, and package.

A useful test is to ask whether the concept would still exist in a Spring application without Boot. A bean, an `ApplicationContext`, `@Configuration`, or Spring MVC controller belongs to Spring Framework. `SpringApplication`, `@SpringBootApplication`, starters, Boot auto-configuration, DevTools, and Boot executable packaging are Boot-facing concepts.

The relationship is therefore additive:

```text
Spring Framework = core application mechanisms
Spring Boot      = conventions + bootstrap + integration around those mechanisms
```

This distinction prevents a common beginner mistake: treating Boot as a separate framework with a separate container. A Boot application normally runs a Spring Framework `ApplicationContext`; Boot is responsible for helping construct and configure that context.

</details>

- [Back to top](#back-to-top)

---

## <a id="convention-over-configuration">Why Convention over Configuration Reduces Setup</a>

<details>
<summary>Click for details</summary>

Convention over configuration means starting from defaults that fit common applications, then overriding only the decisions that differ for your application. It is a way to reduce repetitive setup, not a rule that removes control from the developer.

For example, when the relevant web libraries are present, Boot can prepare a suitable web application context and embedded server integration without requiring the learner to manually wire every infrastructure object. If an application needs a different choice, Boot normally exposes configuration properties or programmatic hooks for that choice.

This creates a useful learning pattern:

```text
common case → accept the default
special case → override the specific decision
unusual architecture → customize or replace the convention
```

The evidence that Boot is convention-based rather than "magic" is that its decisions are visible in startup output, configuration metadata, beans, and runtime behavior. Later chapters use startup evidence to make those defaults concrete.

</details>

- [Back to top](#back-to-top)

---

## <a id="externalized-configuration-orientation">How Configuration Inputs Change a Boot Application</a>

<details>
<summary>Click for details</summary>

A Boot application is intentionally designed to receive configuration from outside the Java code. A database URL, feature setting, application name, port, or profile choice can change between environments even when the compiled application stays the same.

At Fundamentals level, keep one idea: **configuration inputs are one of the forces that shape the application Boot creates**. A value can come from files such as `application.properties` or `application.yml`, environment variables, command-line arguments, and other supported sources. Boot makes those values available to the application and to Boot integrations.

The detailed Config Data model, exact property-source precedence, profiles, binding, validation, and `@ConfigurationProperties` belong to `externalized-configuration`. Here, you only need the orientation that the same application code can behave differently because its configuration inputs differ.

That gives the larger relationship:

```text
same code + same dependencies + different configuration inputs
                         ↓
                 different runtime choices
```

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-capability-map">How the Main Spring Boot Capabilities Fit Together</a>

<details>
<summary>Click for details</summary>

Spring Boot is easier to understand when its major capabilities are seen as cooperating parts rather than one large automatic mechanism.

```text
starters / managed dependencies
        ↓ form the available classpath

classpath -------------------------┐
configuration inputs --------------┼─→ SpringApplication bootstrap
existing application beans --------┘      + ApplicationContext preparation/refresh
                                             ↓
                                  auto-configuration is processed
                                  as configuration during that lifecycle
                                  and reacts to those inputs/context
                                             ↓
                                  resulting ApplicationContext
                                             ↓
                                  runtime / web integrations
```

The important point is that these are cooperating inputs to bootstrap and context refresh, not a pipeline in which starters "run" configuration or auto-configuration runs before `SpringApplication`. Starters mainly shape the classpath; configuration inputs provide values and explicit choices; application beans and bean definitions can affect what automatic configuration should contribute or back off from. During `SpringApplication` bootstrap and `ApplicationContext` refresh, Boot processes auto-configuration as Spring configuration alongside the application's own configuration, producing the resulting context and its runtime integrations.

Packaging, Actuator, testing, and native-image support sit outside this bootstrap chain. They support delivery, operation, verification, or alternate runtime forms after the core application model is understood.

Fundamentals introduces only this relationship. Detailed condition evaluation, ordering, exclusions, and back-off rules belong to `auto-configuration`; detailed bootstrap events and runtime lifecycle belong to `application-runtime`.

</details>

- [Back to top](#back-to-top)

---

## <a id="fundamentals-learning-boundary">What Fundamentals Owns and What Comes Next</a>

<details>
<summary>Click for details</summary>

Fundamentals owns the vocabulary and the end-to-end mental model needed before deeper Spring Boot modules. You should leave this module knowing what Boot adds around Spring, how `SpringApplication` starts a context, what `@SpringBootApplication` contributes, why starters matter, how the classpath influences available behavior, what broad application shapes exist, and where DevTools and executable packaging fit.

When a question becomes about a mechanism in depth, move to its owner:

| Question becomes about... | Continue with... |
| --- | --- |
| Config Data, property precedence, profiles, binding | `externalized-configuration` |
| Conditions, back-off, exclusions, custom auto-configuration | `auto-configuration` |
| Lifecycle events, runners, availability, logging, runtime services | `application-runtime` |
| Embedded-server configuration, TLS, proxy handling, graceful shutdown | `web-runtime` |
| Gradle/Maven plugins, `bootJar`, layers, images, Buildpacks | `build-tooling-packaging` |
| Production endpoints and operational diagnostics | `actuator` |
| Boot test bootstrap and slices | `testing` |
| AOT and native image execution | `native-image` |

This boundary matters because Fundamentals should explain how the pieces connect without duplicating the detailed curricula that own them.

</details>

- [Back to top](#back-to-top)
