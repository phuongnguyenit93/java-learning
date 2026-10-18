<a id="back-to-top"></a>

# Property Sources and Override Precedence

## Menu
- [How Spring Boot Orders Property Sources](#property-source-order)
- [Defaults, Config Data, and Later Overrides](#default-and-config-data-position)
- [Environment Variables, System Properties, Inline JSON, and Command-Line Arguments](#environment-system-json-cli)
- [Why @PropertySource Can Be Too Late for Early Boot Properties](#propertysource-timing)
- [Reasoning About Which Value Wins](#precedence-reasoning)
- [Where Test-Specific Property Sources Belong](#test-precedence-boundary)

## <a id="property-source-order">How Spring Boot Orders Property Sources</a>

<details>
<summary>Click for details</summary>

Spring Boot collects configuration into ordered `PropertySource` objects. For the same key, a source that appears later in Boot's precedence order can override a value from an earlier source. That rule is simple; the full order is not, because Boot supports defaults, Config Data, runtime inputs, container sources, inline JSON, command-line arguments, and test-only sources.

For normal application reasoning, remember the broad progression: programmatic defaults are low precedence; Config Data sits above early defaults; OS environment variables and JVM system properties can override file-based values; `SPRING_APPLICATION_JSON` and command-line arguments sit still higher. Test facilities add their own high-precedence sources during tests.

The safest habit is to reason from the actual source category and Boot's documented order instead of using shortcuts such as "external always wins" or "environment variables always win everything".

</details>

- [Back to top](#back-to-top)

---

## <a id="default-and-config-data-position">Defaults, Config Data, and Later Overrides</a>

<details>
<summary>Click for details</summary>

Packaged Config Data is a good place for application defaults that should travel with the artifact. External Config Data can then provide deployment-specific values without rebuilding the jar. Higher-precedence runtime inputs can override both for operational needs.

For example:

```text
jar: application.properties      app.region=us-east
external application.properties app.region=eu-west
command line                     --app.region=ap-south
```

With all three present, the command-line value is effective. If the command-line argument is removed, the external Config Data value can override the packaged one. This layering supports "sensible default + environment override" without making the application code know where the value came from.

</details>

- [Back to top](#back-to-top)

---

## <a id="environment-system-json-cli">Environment Variables, System Properties, Inline JSON, and Command-Line Arguments</a>

<details>
<summary>Click for details</summary>

Several runtime-oriented sources are intentionally above Config Data. OS environment variables are convenient in containers and managed platforms; Java system properties can be supplied with `-D`; `SPRING_APPLICATION_JSON` can carry a JSON block; command-line options such as `--server.port=9090` are converted into properties by `SpringApplication`.

Their relative order matters. In Boot 3.3, OS environment variables come before Java system properties, `SPRING_APPLICATION_JSON` comes later, and command-line arguments come later still. Therefore a command-line option can override a file, environment variable, or system property for the same key.

Use that power deliberately. High-precedence launch inputs are useful for deployment overrides and experiments, but excessive one-off overrides can make runtime behavior difficult to reproduce unless deployment configuration is tracked clearly.

</details>

- [Back to top](#back-to-top)

---

## <a id="propertysource-timing">Why @PropertySource Can Be Too Late for Early Boot Properties</a>

<details>
<summary>Click for details</summary>

`@PropertySource` is added while the application context is being refreshed. Some Spring Boot settings are needed earlier than that, before refresh begins. The official Boot 3.3 documentation specifically calls out properties such as `logging.*` and `spring.main.*` as examples that may be read too early for an `@PropertySource` to affect them.

That timing difference explains a common failure: the property exists and later appears in the `Environment`, yet Boot has already made an early bootstrap decision using another value or default.

For settings that control early Boot startup, prefer sources available before context refresh, such as system properties, environment variables, command-line arguments, or the supported Config Data mechanisms where applicable. Treat `@PropertySource` as a Spring configuration mechanism with lifecycle timing, not as a universal replacement for Boot Config Data.

</details>

- [Back to top](#back-to-top)

---

## <a id="precedence-reasoning">Reasoning About Which Value Wins</a>

<details>
<summary>Click for details</summary>

When a value is surprising, debug it as a competition between candidates:

1. Identify the exact canonical key.
2. List every source that defines it.
3. Confirm each expected source was actually loaded.
4. Compare those sources using Boot's precedence order.
5. Check profile-specific and imported Config Data because they can alter the file-level candidates.
6. Only then inspect binding or consumption code.

Suppose `app.mode=standard` is packaged, `APP_MODE=safe` is present in the process environment, and `--app.mode=fast` is passed at launch. The effective value is `fast`. Removing the CLI argument exposes `safe`; removing the environment variable then exposes `standard`.

This method is more reliable than editing files until the symptom disappears because it explains both the current value and the fallback chain.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-precedence-boundary">Where Test-Specific Property Sources Belong</a>

<details>
<summary>Click for details</summary>

Boot's complete property-source order includes test-specific entries such as the `properties` attribute on Boot test annotations, `@DynamicPropertySource`, and `@TestPropertySource`. They intentionally sit at high precedence so tests can replace application configuration without changing normal deployment files.

This module needs to acknowledge those sources because they affect the global precedence model, but their detailed lifecycle and test-context behavior belong to the Spring Boot Testing module. Here, the key lesson is boundary-aware reasoning: a value observed in a test may differ from a normal application launch because the test environment contributes additional property sources.

When diagnosing a test-only configuration difference, first determine whether one of those test sources is active before assuming the production precedence model is broken.

</details>

- [Back to top](#back-to-top)
