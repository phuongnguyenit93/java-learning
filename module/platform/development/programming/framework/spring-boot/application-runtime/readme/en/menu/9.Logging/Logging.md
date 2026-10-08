<a id="back-to-top"></a>

# Spring Boot logging system and runtime logging

## Menu
- [Why Does Boot Initialize Logging Before the ApplicationContext?](#early-logging-bootstrap)
- [What Role Does `LoggingSystem` Play?](#logging-system-abstraction)
- [Which Runtime Logging Concerns Can Boot Properties Configure?](#boot-logging-properties)
- [Why Do `-spring` Logging Configuration Files Matter?](#spring-logging-config-files)
- [How Do Logger Groups, Output, and Rotation Fit the Boot Layer?](#logging-groups-output-rotation)
- [Where Does Boot Logging Integration End and Logging Operations Begin?](#logging-observability-handoff)

## <a id="early-logging-bootstrap">Why Does Boot Initialize Logging Before the ApplicationContext?</a>

<details>
<summary>Click for details</summary>

Logging has to work while the application is still starting, including during failures that happen before normal beans exist. Spring Boot therefore initializes its logging system before the `ApplicationContext` is created. That early timing is why logging behaves like runtime bootstrap infrastructure rather than an ordinary application bean.

One consequence is that `@PropertySources` declared on Spring `@Configuration` classes are too late to control logging initialization. Boot can consume supported logging properties from the environment it prepares early, but application bean configuration cannot retroactively change which logging system initialized the bootstrap messages.

When early and later log behavior differ unexpectedly, inspect *when* the configuration becomes available. Many logging startup surprises are lifecycle-order problems rather than logger API problems.

### References

- [Spring Boot 3.3 — Logging](https://docs.spring.io/spring-boot/3.3/reference/features/logging.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="logging-system-abstraction">What Role Does `LoggingSystem` Play?</a>

<details>
<summary>Click for details</summary>

`LoggingSystem` is Spring Boot's abstraction over the supported logging implementations. Boot detects an implementation from the classpath and uses the abstraction to initialize and configure logging before the application context is ready. The application normally works through SLF4J/logging APIs; it does not need to call `LoggingSystem` for everyday logging.

Boot's abstraction explains why the same high-level properties can configure common runtime behavior while native configuration files remain framework-specific. It also provides an explicit escape hatch: the logging system can be selected or disabled through the documented `org.springframework.boot.logging.LoggingSystem` system property when startup truly requires it.

Treat that low-level selection as bootstrap configuration. Choosing logging architecture, appenders, remote transport, retention, indexing, and analysis belongs to the logging/observability domain rather than this Boot integration layer.

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-logging-properties">Which Runtime Logging Concerns Can Boot Properties Configure?</a>

<details>
<summary>Click for details</summary>

Boot exposes common runtime logging controls through configuration properties. `logging.level.<logger-name>` changes logger levels; `logging.level.root` controls the root logger. `logging.file.name` or `logging.file.path` enables file output in addition to console output, while supported pattern and charset settings affect Boot's default configuration.

```yaml
logging:
  level:
    root: INFO
    com.example.orders: DEBUG
  file:
    name: logs/application.log
```

Use these properties when the requirement fits Boot's common abstraction. If the application needs implementation-specific appenders, filters, encoders, or advanced routing, move to the native logging configuration file instead of expecting Boot's generic property surface to model an entire logging framework.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-logging-config-files">Why Do `-spring` Logging Configuration Files Matter?</a>

<details>
<summary>Click for details</summary>

Boot supports the normal native configuration files, but it recommends the `-spring` variants when available: for example `logback-spring.xml` or `log4j2-spring.xml`. The reason is lifecycle timing. A standard file such as `logback.xml` can be loaded directly by the logging implementation too early for Boot to fully control initialization and apply its extensions.

With `logback-spring.xml`, Boot can participate in configuration and features such as its Logback extensions can read Spring profiles or environment properties at the supported phase. `logging.config` can also point to an explicit configuration location.

Use native configuration when the requirement is genuinely implementation-specific. Keep the choice visible, because it changes which layer owns the resulting logging behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="logging-groups-output-rotation">How Do Logger Groups, Output, and Rotation Fit the Boot Layer?</a>

<details>
<summary>Click for details</summary>

Logger groups let an application assign a logical name to several logger categories, then control the group through `logging.level.<group>`. This is useful when a subsystem spans multiple packages and a developer wants one runtime switch rather than several individual logger entries.

Boot also provides convenient output controls. Console logging is available by default; file output can be enabled with `logging.file.name` or `logging.file.path`. With the default Logback integration, Boot exposes properties for file rotation such as maximum file size, history, and total size cap.

These are local runtime configuration features. They do not define how logs are shipped, parsed, retained, correlated, searched, alerted on, or governed across an environment. Those operational concerns belong to the logging/observability curriculum.

</details>

- [Back to top](#back-to-top)

---

## <a id="logging-observability-handoff">Where Does Boot Logging Integration End and Logging Operations Begin?</a>

<details>
<summary>Click for details</summary>

This chapter owns Boot's logging bootstrap, `LoggingSystem`, common logging properties, and the relationship between Boot and native logging configuration. It stops once the application emits correctly configured log events and local outputs.

Log aggregation agents, centralized backends, structured event schemas, retention, indexing, dashboards, alerting, trace/log correlation, and incident investigation are observability responsibilities. Actuator's runtime logger endpoint is also a production-management surface owned by the Actuator module, even though it changes logger levels at runtime.

When a logging issue appears, classify it before editing configuration: "Boot did not apply this startup property" belongs here; "the collector did not ship the file" or "the backend cannot query the field" belongs to infrastructure observability.

</details>

- [Back to top](#back-to-top)
