<a id="back-to-top"></a>

# Exclusions and Diagnostics

## Menu
- [When Should Auto-configuration Be Excluded?](#when-to-exclude-auto-configuration)
- [Class, Name, and Property Exclusions](#exclusion-mechanisms)
- [Condition Evaluation Report](#condition-evaluation-report)
- [Boot Debug Diagnostics](#debug-diagnostics)
- [Diagnosing Unexpected Matches and Missing Beans](#condition-diagnosis-playbook)

## <a id="when-to-exclude-auto-configuration">When Should Auto-configuration Be Excluded?</a>

<details>
<summary>Click for details</summary>

Back-off is best when the application wants the integration but needs to replace one choice. Exclusion is appropriate when the application does not want a particular auto-configuration to participate at all.

Typical reasons include:

- the application deliberately configures that technology through a completely different path;
- a candidate is valid in general but inappropriate for this deployment;
- a migration temporarily needs to disable a Boot-provided integration;
- diagnostics show an auto-configuration is creating infrastructure that the application explicitly does not want.

Do not use exclusion as the first response to every customization need. If a supported bean override or property switch expresses the intent more narrowly, prefer that.

</details>

- [Back to top](#back-to-top)

---

## <a id="exclusion-mechanisms">Class, Name, and Property Exclusions</a>

<details>
<summary>Click for details</summary>

Spring Boot supports several exclusion forms. EnableAutoConfiguration exposes class-based and name-based exclusion attributes, and the spring.autoconfigure.exclude property allows exclusions through external configuration.

Conceptually:

~~~text
exclude by class
→ compile-time access to the auto-configuration type

exclude by name
→ avoid requiring that class as a direct compile-time reference

spring.autoconfigure.exclude
→ externalized application decision
~~~

An exclusion removes a candidate from participation; it is different from a condition naturally evaluating to false. That distinction appears in diagnostics and matters when explaining why a candidate did not apply.

</details>

- [Back to top](#back-to-top)

---

## <a id="condition-evaluation-report">Condition Evaluation Report</a>

<details>
<summary>Click for details</summary>

The Condition Evaluation Report records why auto-configuration conditions matched or did not match. It is one of the most useful pieces of evidence for understanding Boot's decisions.

For a missing AcmeClient, inspect whether:

~~~text
AcmeClientAutoConfiguration was a candidate
        ↓
class condition matched?
        ↓
property condition matched?
        ↓
missing-bean condition matched?
        ↓
candidate excluded?
~~~

The report turns “Boot did not create my bean” into a set of explicit predicates. It is especially valuable because multiple conditions can cooperate on one configuration.

Treat the report as evidence of the selection process, not as a substitute for understanding what each condition means.

</details>

- [Back to top](#back-to-top)

---

## <a id="debug-diagnostics">Boot Debug Diagnostics</a>

<details>
<summary>Click for details</summary>

Boot can log the condition evaluation report when debug diagnostics are enabled. This is useful during startup investigation because positive and negative matches become visible without adding ad-hoc logging to every configuration.

Debug output should answer targeted questions:

- Did Boot discover the candidate?
- Which condition prevented it from matching?
- Did a user bean trigger back-off?
- Was a property value different from the expected value?
- Was the candidate explicitly excluded?

Avoid reading the entire report as noise. Start from the expected auto-configuration class and work outward to the condition that explains its state.

</details>

- [Back to top](#back-to-top)

---

## <a id="condition-diagnosis-playbook">Diagnosing Unexpected Matches and Missing Beans</a>

<details>
<summary>Click for details</summary>

When an expected bean is missing or an unexpected bean appears, diagnose from the outside in:

~~~text
1. confirm the dependency/candidate exists
2. inspect exclusion state
3. inspect configuration-level conditions
4. inspect bean-level conditions and definitions already present
5. inspect property values and application type
6. verify ordering assumptions
7. reproduce the state in a focused context test
~~~

This sequence separates discovery, selection, and bean registration instead of guessing from the final context.

For custom auto-configuration, a focused ApplicationContextRunner test often becomes the smallest executable reproduction. If the test and the real application differ, compare classpath, properties, user configuration, and context type.

Diagnostics explain existing decisions. The next chapter uses the same model proactively to author a custom auto-configuration with narrow conditions, explicit registration, and stable extension points.

</details>

- [Back to top](#back-to-top)
