<a id="back-to-top"></a>

# Loggers and Runtime Diagnostic Endpoints

## Menu
- [What Does the Loggers Endpoint Expose?](#loggers-endpoint)
- [How Do Configured and Effective Logger Levels Differ?](#configured-effective-log-levels)
- [How Can Logger Levels Be Changed at Runtime?](#runtime-log-level-changes)
- [What Does the Thread Dump Endpoint Deliver?](#threaddump-endpoint)
- [What Does the Heap Dump Endpoint Deliver?](#heapdump-endpoint)
- [Where Does Actuator Data Delivery End and JVM or Logging Analysis Begin?](#diagnostic-analysis-boundary)
- [Why Are Powerful Diagnostic Endpoints an Exposure Risk?](#diagnostic-exposure-risk)

## <a id="loggers-endpoint">What Does the Loggers Endpoint Expose?</a>

<details>
<summary>Click for details</summary>

The loggers endpoint exposes the runtime logging configuration known to the application's LoggingSystem. It can list logger names and logger groups, inspect one logger or group, and report the levels needed to understand how logging configuration is currently resolved.

This endpoint consumes Boot's logging integration rather than replacing it. application-runtime owns how Boot initializes LoggingSystem and how normal logging properties/configuration participate in startup. Actuator adds a management operation over the already-running logging system.

Because logger names can reveal package/component structure and write operations can materially increase log volume, the endpoint should be treated as an operational control surface rather than a public application API.

### References

- [Spring Boot 3.3 — Loggers Endpoint](https://docs.spring.io/spring-boot/3.3/api/rest/actuator/loggers.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="configured-effective-log-levels">How Do Configured and Effective Logger Levels Differ?</a>

<details>
<summary>Click for details</summary>

A logger can have a configured level of its own or inherit behavior from its ancestors. The loggers endpoint therefore distinguishes configuredLevel from effectiveLevel. configuredLevel tells you whether that exact logger has an explicit setting; effectiveLevel tells you the level that currently governs logging after inheritance is resolved.

For example, an application package may have no explicit level while the root logger is INFO. The package logger's configured level is absent, but its effective level is INFO. Setting DEBUG on that package changes its configured and effective behavior without changing the root logger.

This distinction prevents a common diagnostic mistake: seeing an effective level and assuming it was configured directly on the logger. Always inspect both when tracing where a runtime logging decision came from.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-log-level-changes">How Can Logger Levels Be Changed at Runtime?</a>

<details>
<summary>Click for details</summary>

For web exposure, the loggers endpoint supports a write operation that can change a logger's configured level while the application is running. An operator can target a specific logger or logger group and set a level such as DEBUG for a focused investigation. Clearing the configured level lets normal inheritance take effect again.

Runtime changes are powerful because they avoid restarting the process, but they are also temporary operational state. A restart normally rebuilds logging configuration from the application's normal configuration sources, so an incident-time change should not be mistaken for a durable configuration update.

Use narrow logger scopes and restore them after the investigation. Broad DEBUG or TRACE logging can create significant I/O volume, reveal sensitive application data, and change performance characteristics.

</details>

- [Back to top](#back-to-top)

---

## <a id="threaddump-endpoint">What Does the Thread Dump Endpoint Deliver?</a>

<details>
<summary>Click for details</summary>

The threaddump endpoint captures a snapshot of JVM thread information and exposes it through the Actuator management surface. The Boot 3.3 REST API can return a structured JSON representation that includes thread identity, state, lock-related information, and stack frames.

The endpoint answers “give me a thread dump from this running process”. It does not interpret the dump. Determining whether threads are deadlocked, blocked on a hot lock, waiting normally, or consuming CPU requires JVM/concurrency analysis beyond Actuator.

Thread dumps can still reveal class names, code paths, thread names, and runtime behavior, so exposure should be restricted. During an incident, collect enough snapshots for analysis rather than repeatedly calling the endpoint without an investigation plan.

### References

- [Spring Boot 3.3 — Thread Dump REST API](https://docs.spring.io/spring-boot/3.3/api/rest/actuator/threaddump.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="heapdump-endpoint">What Does the Heap Dump Endpoint Deliver?</a>

<details>
<summary>Click for details</summary>

The heapdump web endpoint returns a heap dump from the running JVM as binary data. On HotSpot the file is HPROF format; on OpenJ9 it is PHD format. Heap dumps can be large and generating one can add runtime cost, so the endpoint is an incident diagnostic tool rather than a routine polling target.

A heap dump can contain object graphs, strings, cached data, credentials, request content, and other sensitive material that happened to be retained in memory. Treat the downloaded artifact with the same or greater protection as production data.

Actuator's responsibility ends at producing the dump through a Boot management endpoint. Object-retention analysis, dominator trees, leak investigation, GC reasoning, and tooling such as heap analyzers belong to JVM/runtime diagnostics.

</details>

- [Back to top](#back-to-top)

---

## <a id="diagnostic-analysis-boundary">Where Does Actuator Data Delivery End and JVM or Logging Analysis Begin?</a>

<details>
<summary>Click for details</summary>

Loggers, thread dumps, and heap dumps illustrate a recurring Actuator boundary: the endpoint delivers or controls diagnostic state, while a specialist domain explains what that state means. Actuator can tell you the effective logger level, but logging analysis determines whether the emitted events explain the incident.

Actuator can return a thread snapshot, but Java concurrency/JVM tooling determines whether a wait state is normal or pathological. Actuator can return heap contents, but memory-analysis tools determine which objects dominate retained memory and why.

Keeping this boundary prevents the module from becoming a logging or JVM performance course. The learner should know how to obtain evidence safely, recognize its management semantics, and then hand that evidence to the correct analysis discipline.

</details>

- [Back to top](#back-to-top)

---

## <a id="diagnostic-exposure-risk">Why Are Powerful Diagnostic Endpoints an Exposure Risk?</a>

<details>
<summary>Click for details</summary>

Diagnostic endpoints concentrate privileged information. Logger controls can increase verbosity or reveal data in logs. Thread dumps expose execution paths and synchronization state. Heap dumps may contain secrets or user data. Similar endpoints such as env, configprops, mappings, and beans can reveal application structure.

Do not expose these endpoints broadly simply because authentication exists. Combine a minimal exposure list with network placement and authorization appropriate to the operational audience. Consider whether a dump endpoint should be reachable only during a controlled incident path.

Also account for operational cost. Large heap downloads, repeated dumps, or wide TRACE logging can stress the same application being diagnosed. Safe diagnostics include both confidentiality and resource-impact decisions.

</details>

- [Back to top](#back-to-top)
