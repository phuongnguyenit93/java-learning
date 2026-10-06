<a id="back-to-top"></a>

# Environment, Placeholders, and @Value

## Menu
- [Reading Resolved Values Through Environment](#environment-access)
- [How ${...} Placeholders Resolve](#placeholder-resolution)
- [Placeholder Defaults and Missing Values](#placeholder-defaults)
- [Focused Scalar Injection with @Value](#value-injection)
- [Why Canonical Kebab-Case Property Names Matter](#canonical-property-names)
- [@Value vs @ConfigurationProperties](#value-vs-configuration-properties)
- [SpEL Support and the @Value Boundary](#spel-boundary)

## <a id="environment-access">Reading Resolved Values Through Environment</a>

<details>
<summary>Click for details</summary>

After Spring Boot has assembled its property sources, application code can ask Spring's Environment for the effective value of a key. The important mental model is that Environment is the resolved view over all participating sources; it is not another configuration file.

~~~java
@Component
class PricingPolicy {
    PricingPolicy(Environment environment) {
        String currency = environment.getProperty("shop.currency", "USD");
    }
}
~~~

A lookup therefore observes the same precedence rules learned earlier. If shop.currency exists in application.properties but a higher-precedence command-line argument supplies --shop.currency=EUR, Environment returns EUR. Use direct Environment access when lookup is genuinely dynamic or infrastructure-oriented; do not turn repeated related lookups into a manual replacement for @ConfigurationProperties.

</details>

- [Back to top](#back-to-top)

---

## <a id="placeholder-resolution">How ${...} Placeholders Resolve</a>

<details>
<summary>Click for details</summary>

A ${...} placeholder asks the property resolver to substitute a value from the Environment. Placeholders can appear in annotations and in configuration values themselves, so one property can be composed from another resolved property.

~~~properties
app.name=orders
app.description=${app.name} service
~~~

The placeholder is resolved when the consuming mechanism asks for the value. It does not create a new property source or change precedence: first Boot establishes the effective property set, then placeholder resolution reads from that set. This distinction matters when debugging because an unexpected placeholder value usually means either the referenced key resolved differently than expected or the placeholder itself was written incorrectly.

</details>

- [Back to top](#back-to-top)

---

## <a id="placeholder-defaults">Placeholder Defaults and Missing Values</a>

<details>
<summary>Click for details</summary>

A placeholder can provide a fallback after a colon, for example ${region:us-east}. The fallback is used when the referenced property cannot be resolved; it is not a higher-precedence property and is not published as a new Environment entry.

~~~properties
service.region=${REGION_NAME:local}
~~~

Defaults are useful for small, safe fallbacks, but a default can also hide a missing required setting. If absence should prevent startup, prefer a required binding plus validation rather than silently supplying a placeholder default. This is a design choice: convenience defaults and configuration contracts solve different problems.

</details>

- [Back to top](#back-to-top)

---

## <a id="value-injection">Focused Scalar Injection with @Value</a>

<details>
<summary>Click for details</summary>

@Value is convenient for a small number of focused scalar values. It delegates expression/placeholder resolution to the Spring container and can inject the resolved result into a constructor parameter, field, or method parameter.

~~~java
@Component
class Banner {
    Banner(@Value("${app.title:Demo}") String title) {
        this.title = title;
    }
}
~~~

The value still comes from the Environment assembled by Boot, so changing the winning property source changes what is injected. @Value is best when the configuration is local and small. Once several values form one conceptual namespace, a typed @ConfigurationProperties object gives clearer ownership, validation, metadata, and refactoring support.

</details>

- [Back to top](#back-to-top)

---

## <a id="canonical-property-names">Why Canonical Kebab-Case Property Names Matter</a>

<details>
<summary>Click for details</summary>

When writing placeholders, Spring Boot recommends the canonical lower-case kebab form, such as ${demo.item-price}. That form lets Boot apply the broadest compatible relaxed-name lookup across supported property sources.

Using ${demo.itemPrice} is more restrictive and does not enable the relaxed placeholder lookup that lets the canonical key also match forms such as the corresponding environment-variable name. The practical rule is simple: design application configuration keys in lower-case kebab form and refer to them in placeholders using that canonical form, even when a physical source such as an OS environment variable must use a different spelling.

</details>

- [Back to top](#back-to-top)

---

## <a id="value-vs-configuration-properties">@Value vs @ConfigurationProperties</a>

<details>
<summary>Click for details</summary>

@Value and @ConfigurationProperties consume the same resolved configuration but optimize for different shapes. @Value is concise for isolated values and supports SpEL. @ConfigurationProperties groups a namespace into a typed object and provides full relaxed binding, structured binding, validation integration, and configuration metadata.

| Need | Prefer |
| --- | --- |
| One local scalar or a SpEL expression | @Value |
| A reusable group of related settings | @ConfigurationProperties |
| Nested/list/map configuration | @ConfigurationProperties |
| IDE metadata for custom keys | @ConfigurationProperties |

The choice is therefore about the application-facing configuration contract, not about which annotation has higher precedence. Precedence has already been decided before either consumer receives the value.

</details>

- [Back to top](#back-to-top)

---

## <a id="spel-boundary">SpEL Support and the @Value Boundary</a>

<details>
<summary>Click for details</summary>

@Value can evaluate Spring Expression Language (SpEL), whereas @ConfigurationProperties deliberately does not. This makes @Value powerful for local expression-based injection, but it also means it has a different contract from plain external configuration binding.

A SpEL-looking string stored in application.properties is not evaluated merely because Config Data loaded it. Expression evaluation happens when a consumer such as @Value interprets the value. Keeping that boundary clear prevents two misconceptions: Config Data is not an expression engine, and @ConfigurationProperties is intentionally focused on data binding rather than arbitrary expression execution.

For configuration that should remain portable, inspectable data, prefer ordinary property values and typed binding. Reserve SpEL for cases where expression semantics are genuinely part of the consuming code.

</details>

- [Back to top](#back-to-top)
