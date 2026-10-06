<a id="back-to-top"></a>

# Validation and Configuration Metadata

## Menu
- [Why Validate Configuration at Startup?](#configuration-validation-purpose)
- [Validating @ConfigurationProperties with @Validated](#validated-configuration-properties)
- [Nested Validation with @Valid](#nested-validation)
- [Binding, Conversion, and Validation Failures](#binding-vs-validation-failure)
- [Why Configuration Metadata Exists](#configuration-metadata-purpose)
- [Generating Metadata with the Configuration Processor](#configuration-processor)
- [Metadata, IDE Assistance, and Tooling Boundary](#metadata-tooling-boundary)

## <a id="configuration-validation-purpose">Why Validate Configuration at Startup?</a>

<details>
<summary>Click for details</summary>

Type conversion can prove that "8080" is an integer, but it cannot prove that 8080 is acceptable for a particular configuration contract. Validation adds domain constraints after binding so an invalid application configuration can fail close to startup instead of surfacing later as an obscure runtime bug.

Typical constraints include required host names, positive pool sizes, non-blank identifiers, and bounded timeouts. The goal is fail-fast feedback with a message tied to the configuration property, not to reimplement business validation inside a property class.

~~~text
resolved properties
      ↓
binding + conversion
      ↓
typed configuration object
      ↓
constraint validation
      ↓
valid object OR startup failure
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="validated-configuration-properties">Validating @ConfigurationProperties with @Validated</a>

<details>
<summary>Click for details</summary>

Annotate a @ConfigurationProperties type with Spring's @Validated and place Jakarta Validation constraints on its properties. A compliant validation implementation must be present on the classpath.

~~~java
@ConfigurationProperties("client")
@Validated
public record ClientProperties(
        @NotBlank String baseUrl,
        @Positive int maxConnections) {
}
~~~

If client.max-connections resolves to -1, binding can still produce an int, but validation rejects the resulting object and startup reports the configuration problem. This makes the distinction between syntax/type errors and domain-invalid values explicit.

Validation annotations express the contract of the configuration type. Generic Bean Validation mechanics and advanced constraint-authoring belong to their dedicated validation curriculum.

</details>

- [Back to top](#back-to-top)

---

## <a id="nested-validation">Nested Validation with @Valid</a>

<details>
<summary>Click for details</summary>

Constraints on a nested configuration object are not automatically a substitute for cascading validation. When a nested object must also be checked, mark the associated field or component with @Valid so the validator traverses into it.

~~~java
@ConfigurationProperties("mail")
@Validated
public class MailProperties {
    @Valid
    private final Security security = new Security();

    public Security getSecurity() { return security; }

    public static class Security {
        @NotBlank
        private String protocol;
        // getter/setter
    }
}
~~~

Without the cascade marker, the outer object may be validated while constraints on the nested object are not visited in the way the configuration contract expects. Treat nested validation as part of the public configuration shape and test the failure path for required nested settings.

</details>

- [Back to top](#back-to-top)

---

## <a id="binding-vs-validation-failure">Binding, Conversion, and Validation Failures</a>

<details>
<summary>Click for details</summary>

Several startup failures can look like "bad configuration" but happen at different stages.

| Stage | Example | Meaning |
| --- | --- | --- |
| Loading | required imported file is missing | Boot could not obtain configuration data |
| Binding/conversion | timeout=banana for a Duration | value cannot become the target type |
| Validation | max-connections=-1 with @Positive | type is valid, domain constraint is not |

This stage model makes diagnostics faster. Do not fix a conversion error by weakening a validation rule, and do not search property precedence when the real problem is a missing required import. Read the exception chain and binding/validation report with the pipeline stage in mind.

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-metadata-purpose">Why Configuration Metadata Exists</a>

<details>
<summary>Click for details</summary>

Configuration metadata describes supported keys so tools can help users discover and correctly edit configuration. Spring Boot jars use META-INF/spring-configuration-metadata.json to publish information such as property names, target types, descriptions, defaults, deprecation data, and value hints.

Metadata does not create properties, change precedence, or perform binding. It is descriptive tooling data about the configuration contract. That separation is useful: the runtime behavior remains governed by Environment and binding, while IDEs can offer completion and documentation without starting the application.

For application-owned configuration libraries, good metadata turns a typed @ConfigurationProperties model into a much easier configuration experience for downstream users.

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-processor">Generating Metadata with the Configuration Processor</a>

<details>
<summary>Click for details</summary>

The spring-boot-configuration-processor annotation processor can generate metadata at compile time from @ConfigurationProperties types. With Gradle, it is normally placed on the annotationProcessor configuration rather than the runtime classpath.

~~~groovy
dependencies {
    annotationProcessor "org.springframework.boot:spring-boot-configuration-processor"
}
~~~

The processor inspects bindable properties and documentation available in source, then writes metadata under META-INF. Because generation happens at compile time, it is not evidence that a property was supplied at runtime or that validation succeeded.

Advanced/manual metadata can supplement cases the processor cannot infer, but metadata generation should follow the actual configuration contract rather than inventing keys that code does not consume.

</details>

- [Back to top](#back-to-top)

---

## <a id="metadata-tooling-boundary">Metadata, IDE Assistance, and Tooling Boundary</a>

<details>
<summary>Click for details</summary>

IDE completion and metadata are development aids, not runtime configuration authorities. A key can still bind at runtime even if custom metadata is missing, and a metadata entry does not guarantee that a particular runtime path actually uses the key.

~~~text
@ConfigurationProperties source
        ↓ compile time
configuration processor
        ↓
spring-configuration-metadata.json
        ↓
IDE completion / documentation
~~~

Keep three concerns separate when debugging: runtime value resolution, typed binding/validation, and compile-time tooling metadata. If completion is wrong, inspect metadata generation. If the application receives the wrong value, inspect property sources and binding instead.

</details>

- [Back to top](#back-to-top)
