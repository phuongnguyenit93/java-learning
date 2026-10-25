<a id="back-to-top"></a>

# Externalized Configuration Mental Model

## Menu
- [What Is Externalized Configuration and Why Does It Exist?](#externalized-configuration-purpose)
- [Configuration Values vs Application Code](#configuration-vs-application-code)
- [Environment as the Resolved View of Configuration](#environment-resolved-view)
- [Major Configuration Source Categories](#configuration-source-categories)
- [From Configuration Input to Effective Runtime Value](#configuration-resolution-pipeline)
- [What This Module Owns and What It Hands Off](#module-ownership-boundary)

## <a id="externalized-configuration-purpose">What Is Externalized Configuration and Why Does It Exist?</a>

<details>
<summary>Click for details</summary>

Externalized configuration means keeping environment-dependent values outside the application logic so the same compiled application can run with different settings. A service may need a different database URL, feature threshold, or HTTP port in development and production, while the Java code that implements the service stays the same.

Spring Boot turns those inputs into configuration properties that application code can consume. The important mental model is not "one configuration file". It is **many possible inputs feeding one resolved configuration view**. Files are only one source; environment variables, JVM system properties, command-line arguments, and other property sources can participate as well.

This separation improves portability and deployment flexibility, but it also creates a new question: if several sources define the same key, which value wins? The next chapters build that answer through property-source precedence, Config Data loading, profiles, and binding.

```text
same application artifact
        +
environment-specific inputs
        ↓
effective configuration
        ↓
runtime behavior
```

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-vs-application-code">Configuration Values vs Application Code</a>

<details>
<summary>Click for details</summary>

Configuration is best suited to values that may legitimately vary without changing the application's business logic: endpoints, timeouts, limits, credentials references, feature flags, or deployment-specific names. Application code should still own the rules that interpret those values and decide what the program does.

For example, `payment.retry.max-attempts=4` is configuration. The retry algorithm, which exceptions are retryable, and what happens after the fourth failure are application behavior. Moving every decision into properties would make the program harder to understand and test; hard-coding every deployment value in Java would make the artifact harder to reuse.

A useful test is: **Could operations change this value between environments while the intended application capability remains the same?** If yes, it is a strong configuration candidate. If changing it would redefine core domain rules or program structure, it likely belongs in code instead.

</details>

- [Back to top](#back-to-top)

---

## <a id="environment-resolved-view">Environment as the Resolved View of Configuration</a>

<details>
<summary>Click for details</summary>

Spring's `Environment` is the application-facing view of configuration after the available property sources have been assembled and ordered. Instead of asking each source directly, application code can ask the `Environment` for a property and receive the value that is effective according to precedence.

```java
String region = environment.getProperty("app.region");
```

The `Environment` does not imply that values came from one place. The effective `app.region` might have originated in packaged Config Data, an external file, an OS environment variable, or a command-line argument. That distinction matters when debugging: the value is simple to read, but explaining **why that value won** requires understanding the contributing property sources.

This module focuses on Boot's configuration-loading and resolution behavior around the `Environment`. Detailed Spring Framework implementation internals of `Environment` and `PropertySource` remain outside this module's ownership.

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-source-categories">Major Configuration Source Categories</a>

<details>
<summary>Click for details</summary>

The most common Spring Boot configuration sources fall into a few practical categories:

- **application defaults** supplied programmatically;
- **Config Data**, such as `application.properties`, `application.yaml`, profile-specific files, and imported resources;
- **host/runtime inputs**, including OS environment variables and JVM system properties;
- **inline or launch-time inputs**, such as `SPRING_APPLICATION_JSON` and command-line arguments;
- **framework/container sources**, such as JNDI or servlet initialization parameters when relevant;
- **test-only overrides**, which exist but are owned in depth by the testing module.

The category tells you *where a value can come from*. Precedence tells you *which candidate is effective when keys collide*. Keeping those two questions separate prevents a common mistake: assuming an external file always overrides every other source merely because it is outside the jar.

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-resolution-pipeline">From Configuration Input to Effective Runtime Value</a>

<details>
<summary>Click for details</summary>

When Boot starts, configuration resolution can be understood as a pipeline rather than as a single file read:

```text
discover available configuration sources
        ↓
load Config Data and other property sources
        ↓
activate relevant profile-specific documents
        ↓
apply ordering / precedence across the participating sources/documents
        ↓
resolve an effective value for each requested key
        ↓
consume directly or bind into typed configuration objects
```

Consider `server.port`. A packaged file might define `8080`, an external file might define `8081`, and the launch command might include `--server.port=9090`. The application ultimately observes one effective value because the property sources have an order. The same pattern later applies when resolving custom keys such as `orders.timeout`.

This pipeline is also a debugging checklist. When a value is unexpected, first ask whether the expected source was loaded, then whether the expected profile/document is active, then compare the participating candidates by precedence, and only after that inspect value consumption or binding.

</details>

- [Back to top](#back-to-top)

---

## <a id="module-ownership-boundary">What This Module Owns and What It Hands Off</a>

<details>
<summary>Click for details</summary>

This module owns the Spring Boot side of externalized configuration: source loading, precedence, Config Data locations and imports, profiles, resolved-value consumption, `@ConfigurationProperties`, relaxed/complex binding, validation integration, and configuration metadata.

Several adjacent topics are intentionally handed off:

- Spring Framework owns the deeper `Environment`, conversion, bean-factory, and validation mechanics.
- Spring Boot Testing owns test-specific override mechanisms and test-context behavior.
- Spring Boot Auto-Configuration consumes configuration values to make conditional decisions, but condition evaluation belongs to that module.
- Spring Cloud Config and secret-management products own remote/distributed configuration and secret storage workflows.
- Runtime and Actuator modules may expose diagnostics or consume resolved values, but they do not redefine the precedence model taught here.

Keeping these boundaries explicit lets this module answer one coherent question: **how does Boot turn configuration inputs into reliable values that the application can use?**

</details>

- [Back to top](#back-to-top)
