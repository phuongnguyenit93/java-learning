<a id="back-to-top"></a>

# Info and Environment-facing Endpoints

## Menu
- [What Is the Info Endpoint For?](#info-endpoint-purpose)
- [How Do InfoContributor-style Contributions Build Operational Metadata?](#info-contributors)
- [What Can Environment-facing Endpoints Reveal About the Running Application?](#environment-facing-endpoints)
- [How Are Potentially Sensitive Values Treated?](#sensitive-value-sanitization)
- [Why Are Info and Environment Views an Access Decision?](#info-env-access-boundary)
- [Where Does Endpoint Inspection Hand Off to Externalized Configuration?](#externalized-config-handoff)

## <a id="info-endpoint-purpose">What Is the Info Endpoint For?</a>

<details>
<summary>Click for details</summary>

The info endpoint is a place for concise application information that is useful to operators and automation: build identity, version information, source revision, or other non-transactional metadata. Its content is assembled from InfoContributor beans rather than from one hard-coded application-info object.

The endpoint is not a general debug dump. Good info data is stable enough to help identify what is running and small enough to be safely returned on demand. Information that changes at high frequency belongs in metrics or another telemetry channel, while secrets and detailed configuration do not belong in info merely because operators may sometimes need them.

Like other Actuator endpoints, info must still be exposed before it is remotely reachable. Boot 3.3's default remote exposure remains intentionally narrow, so enabling or contributing info does not imply that it is automatically public.

### References

- [Spring Boot 3.3 — Application Information](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="info-contributors">How Do InfoContributor-style Contributions Build Operational Metadata?</a>

<details>
<summary>Click for details</summary>

InfoContributor is the extension point that adds a named contribution to the info response. Boot auto-configures contributors when relevant data or supporting configuration is available. In Boot 3.3, the env, java, os, and process contributors are disabled by default, while build and git are enabled by default when their prerequisite `META-INF/build-info.properties` or `git.properties` data exists.

Applications can register their own InfoContributor when an operationally useful piece of metadata does not fit the built-in contributors. A custom contributor should add a small structured value with a clear meaning, such as deployment flavor or model version, rather than executing expensive queries every time /info is called.

Individual built-in contributors are controlled by `management.info.<id>.enabled`, and `management.info.defaults.enabled=false` can disable contributors that are otherwise enabled by default. These contributor defaults are independent from whether the info endpoint itself is exposed over HTTP or JMX.

</details>

- [Back to top](#back-to-top)

---

## <a id="environment-facing-endpoints">What Can Environment-facing Endpoints Reveal About the Running Application?</a>

<details>
<summary>Click for details</summary>

Environment-facing endpoints answer configuration-oriented diagnostic questions. The env endpoint presents information from Spring's ConfigurableEnvironment and its property sources, while configprops presents values bound to ConfigurationProperties beans. These views help explain what the running process can see and what structured configuration objects contain.

They are diagnostic projections, not the source of configuration truth. Actuator does not change the Environment, resolve property-source precedence, or teach how Config Data, profiles, environment variables, and command-line arguments were merged. Those mechanics remain in externalized-configuration.

Because these endpoints can reveal property names, source structure, and potentially valuable operational context even when values are hidden, treat them as privileged diagnostics rather than routine public endpoints.

</details>

- [Back to top](#back-to-top)

---

## <a id="sensitive-value-sanitization">How Are Potentially Sensitive Values Treated?</a>

<details>
<summary>Click for details</summary>

Spring Boot sanitizes values returned by sensitive configuration-oriented endpoints. The env and configprops endpoint models can replace values with a masked representation, and Boot provides show-values policies that control when original values may be returned. For HTTP, `when-authorized` means an authenticated user must have one of the configured endpoint roles, with any authenticated user authorized when no roles are configured. For JMX, Actuator treats every user as authorized for this show-values decision.

Sanitization reduces accidental disclosure, but it is not a reason to expose configuration endpoints broadly. Property names, source names, object structure, and values that are not recognized as sensitive can still reveal useful information to an attacker. Applications can also extend sanitization behavior when organization-specific secret naming requires it.

The strongest control remains limiting exposure and authorization. The JMX authorization rule above does not secure JMX itself: JMX exposure, remote connectivity, authentication/access control, and network placement still need deliberate protection. Sanitization is defense in depth for a management representation, not a secret-management system and not a replacement for storing credentials safely.

</details>

- [Back to top](#back-to-top)

---

## <a id="info-env-access-boundary">Why Are Info and Environment Views an Access Decision?</a>

<details>
<summary>Click for details</summary>

Info and environment views serve different risk profiles. A carefully curated info response may be appropriate for a broader operational audience, while env and configprops often reveal enough structure to deserve restricted access. The correct policy depends on the deployment and on the data contributed by the application.

Before exposing these endpoints, review both the values and the metadata around them. Ask whether an unauthenticated caller needs the information, whether an operator can get it through a safer internal channel, and whether the endpoint is reachable only from a management network.

Exposure configuration, network placement, security authorization, and sanitization should reinforce one another. None of them alone should be treated as the complete protection for sensitive management data.

</details>

- [Back to top](#back-to-top)

---

## <a id="externalized-config-handoff">Where Does Endpoint Inspection Hand Off to Externalized Configuration?</a>

<details>
<summary>Click for details</summary>

Actuator helps answer “what configuration-related state can this running application report?” Externalized configuration answers “how did Boot resolve that state from property sources, Config Data, profiles, command-line arguments, environment variables, and binding rules?”

For example, env can show that a property is present in a particular property source, and configprops can show the value bound into a ConfigurationProperties object. If the value is unexpected, the investigation then leaves Actuator and follows the precedence/binding rules in externalized-configuration.

Keep the handoff explicit in documentation and troubleshooting. Actuator is a runtime inspection surface. It should not become a second curriculum for Config Data imports, relaxed binding, profile activation, placeholder resolution, or configuration-property validation.

</details>

- [Back to top](#back-to-top)
