<a id="back-to-top"></a>

# Reading Spring Boot Startup as Evidence

## Menu
- [Why Treat Startup Output as Evidence?](#startup-as-evidence)
- [What Can You Learn from the Startup Output?](#reading-startup-output)
- [How Do Boot Defaults Stay Observable and Overridable?](#defaults-observable-overridable)
- [Which Beginner-Level Startup Failure Classes Matter?](#startup-failure-classes)
- [When Should Diagnosis Hand Off to Auto-Configuration or Actuator?](#diagnostics-handoff)

## <a id="startup-as-evidence">Why Treat Startup Output as Evidence?</a>

<details>
<summary>Click for details</summary>

Boot is easiest to learn when startup is treated as observable behavior. A successful `main` call is not evidence that "Boot did everything automatically"; the startup log shows what application is starting, which profiles are active, which runtime components are being initialized, and whether the context reached the running state.

Reading this output creates a feedback loop between the mental model and the real process:

```text
change dependency / configuration / code
        ↓
start application
        ↓
read startup evidence
        ↓
compare observed application with intended application
```

This habit becomes valuable before deeper diagnostics are introduced. It teaches you to ask what Boot actually created and selected instead of assuming that every default is fixed or hidden.

</details>

- [Back to top](#back-to-top)

---

## <a id="reading-startup-output">What Can You Learn from the Startup Output?</a>

<details>
<summary>Click for details</summary>

Typical startup output exposes several useful clues. The Boot banner identifies the Boot line being launched. The initial "Starting ..." message identifies the application and often the Java process context. Profile messages tell you whether profiles are active. Context or server messages show which broad runtime shape was initialized. The final "Started ... in ... seconds" message is evidence that startup completed.

For a web application you may also see a server implementation and port being initialized. For a non-web application, those server messages should be absent. This makes logs an easy way to verify the application-shape model from the previous chapters.

Do not memorize exact log wording as an API contract. Logging configuration and implementation details can change. Instead, learn the categories of evidence: application identity, configuration/profile orientation, context/runtime selection, startup duration, and failures.

</details>

- [Back to top](#back-to-top)

---

## <a id="defaults-observable-overridable">How Do Boot Defaults Stay Observable and Overridable?</a>

<details>
<summary>Click for details</summary>

Boot defaults are useful because they let a common application start with few explicit settings, but a default is still a decision that can be observed and changed. The server shape, application name, banner mode, configuration values, and many integration choices have documented configuration or programmatic control points.

This gives a practical definition of convention over configuration:

```text
no explicit choice
→ Boot applies a documented default when applicable

explicit supported choice
→ application configuration replaces or adjusts that default
```

The exact precedence rules for externalized properties belong to `externalized-configuration`. The Fundamentals lesson is that when you see a default in logs or behavior, you should look for the owning configuration/documentation rather than assume the behavior is hard-coded and untouchable.

</details>

- [Back to top](#back-to-top)

---

## <a id="startup-failure-classes">Which Beginner-Level Startup Failure Classes Matter?</a>

<details>
<summary>Click for details</summary>

For a beginner, startup failures are easier to diagnose by first locating the layer that failed.

1. **Java/process failure:** the JVM cannot launch the main class, a required class is missing, or process arguments are invalid before Boot meaningfully starts.
2. **Spring container failure:** the context cannot be refreshed because bean creation, dependency injection, configuration parsing, or another core container operation fails.
3. **Boot integration/configuration failure:** Boot starts but a selected integration cannot be configured, required configuration is missing, or an application/environment choice conflicts with the available setup.

The categories can overlap because Boot builds on Spring. They are a triage tool, not exception taxonomies. Start from the root cause and the earliest meaningful failure message rather than fixing whichever stack-frame name looks most familiar.

Once the problem is specifically about **why an auto-configuration matched**, move to auto-configuration diagnostics. Once the application is running and you need production runtime evidence, move to Actuator.

</details>

- [Back to top](#back-to-top)

---

## <a id="diagnostics-handoff">When Should Diagnosis Hand Off to Auto-Configuration or Actuator?</a>

<details>
<summary>Click for details</summary>

Fundamentals owns basic startup reading. The `auto-configuration` module owns the Condition Evaluation Report and detailed reasoning about why a particular automatic configuration matched, did not match, or backed off. That is the right destination when the question is about conditional configuration decisions.

Actuator owns production-ready runtime surfaces such as health, metrics, loggers, and operational endpoints. That is the right destination when startup has succeeded and the question is about the state of a running application in an operational environment.

```text
startup did not produce expected configuration
→ auto-configuration diagnostics

application is running; need operational state
→ Actuator
```

Keeping the two destinations separate prevents startup logs from being treated as a complete production monitoring system.

</details>

- [Back to top](#back-to-top)
