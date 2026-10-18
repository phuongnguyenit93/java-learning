<a id="back-to-top"></a>

# Understanding and Customizing Test Auto-Configuration

## Menu
- [Why Does Boot Provide Test-Specific Auto-Configuration?](#test-auto-configuration-purpose)
- [How Do Slice Annotations Choose Their Imported Auto-Configuration?](#slice-imported-auto-configuration)
- [How Do `@AutoConfigure...` Annotations Add or Tune Test Facilities?](#auto-configure-annotations)
- [How Do You Exclude Auto-Configuration from a Boot Test?](#exclude-test-auto-configuration)
- [When Should `@ImportAutoConfiguration` Add Test Infrastructure?](#import-auto-configuration)
- [How Do You Diagnose a Missing Bean in a Focused Test Context?](#test-context-missing-bean-diagnosis)
- [Where Does Test Auto-Configuration Hand Off to Boot's General Auto-Configuration Model?](#test-auto-config-ownership-boundary)

## <a id="test-auto-configuration-purpose">Why Does Boot Provide Test-Specific Auto-Configuration?</a>

<details>
<summary>Click for details</summary>
Production auto-configuration is designed to assemble a running application. Tests often need a different set of supporting beans: mock clients, embedded test infrastructure, test database replacement, JSON testers, or other facilities that should not become part of production startup.

Boot test auto-configuration supplies those facilities only in test contexts and lets slice annotations import curated subsets. This keeps testing support declarative without treating test infrastructure as normal application configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="slice-imported-auto-configuration">How Do Slice Annotations Choose Their Imported Auto-Configuration?</a>

<details>
<summary>Click for details</summary>
Each Boot slice is associated with a defined set of auto-configuration imports. The list is purpose-specific: web slices import web testing support, data slices import persistence-oriented support, and other slices select infrastructure for their own technology.

Use Boot's test-slice appendix when the exact imported set matters. Avoid guessing that a bean should exist just because its production auto-configuration exists somewhere in the application.

### References

- [Spring Boot 3.3 — Test Slices](https://docs.spring.io/spring-boot/3.3/appendix/test-auto-configuration/slices.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="auto-configure-annotations">How Do `@AutoConfigure...` Annotations Add or Tune Test Facilities?</a>

<details>
<summary>Click for details</summary>
Annotations such as `@AutoConfigureMockMvc`, `@AutoConfigureWebTestClient`, and `@AutoConfigureTestDatabase` add or tune a focused testing facility around an existing test context. They are useful when the primary context choice is correct but one supporting capability needs explicit configuration.

This is different from choosing another slice. The primary annotation defines the context boundary; `@AutoConfigure...` refines a facility inside that boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="exclude-test-auto-configuration">How Do You Exclude Auto-Configuration from a Boot Test?</a>

<details>
<summary>Click for details</summary>
When an auto-configuration is inappropriate for a test scenario, Boot test annotations and auto-configuration controls can exclude it explicitly. Exclusion should target a known configuration that is causing unwanted infrastructure or conflicting behavior.

Before excluding anything, verify why the configuration matched. A missing dependency, incorrect property, or misunderstood slice boundary is often a better root cause to fix than permanently removing useful auto-configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="import-auto-configuration">When Should `@ImportAutoConfiguration` Add Test Infrastructure?</a>

<details>
<summary>Click for details</summary>
Use `@ImportAutoConfiguration` when a focused test needs a specific additional auto-configuration that is not part of the slice's default set. Boot handles auto-configuration imports specially, including their ordering and condition model.

Do not use ordinary `@Import` to import an auto-configuration class as if it were normal user configuration. `@Import` remains appropriate for ordinary application/test configuration classes.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-context-missing-bean-diagnosis">How Do You Diagnose a Missing Bean in a Focused Test Context?</a>

<details>
<summary>Click for details</summary>
First identify the chosen test boundary. Ask whether the missing type should be selected by the slice's scanning rules, supplied by its imported auto-configuration, or provided explicitly as a collaborator. Then inspect auto-configuration conditions and exclusions if the bean should have been created automatically.

This order avoids “fixing” a slice by importing unrelated production layers. A missing service in `@WebMvcTest`, for example, is often expected and should be supplied as a focused collaborator rather than discovered through broad component scanning.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-auto-config-ownership-boundary">Where Does Test Auto-Configuration Hand Off to Boot's General Auto-Configuration Model?</a>

<details>
<summary>Click for details</summary>
This module owns how Boot test annotations select, add, or exclude auto-configuration in test contexts. The general rules for `@AutoConfiguration`, conditions, ordering, back-off, exclusions, and condition diagnostics belong to the Spring Boot auto-configuration module.

When a failure depends on why a condition matched or how two auto-configurations are ordered, continue with the general auto-configuration model rather than duplicating it here.

</details>

- [Back to top](#back-to-top)
