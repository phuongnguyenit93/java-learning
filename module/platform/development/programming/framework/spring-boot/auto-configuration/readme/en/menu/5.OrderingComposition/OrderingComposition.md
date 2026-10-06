<a id="back-to-top"></a>

# Ordering and Composition

## Menu
- [Configuration Definition Order vs Bean Creation Order](#configuration-order-vs-bean-order)
- [Before and After Relationships](#before-after-ordering)
- [Ordering Independent Auto-configurations](#auto-configure-order)
- [Composing Conditional Configuration](#conditional-composition)
- [Isolating Optional Technology](#optional-technology-isolation)

## <a id="configuration-order-vs-bean-order">Configuration Definition Order vs Bean Creation Order</a>

<details>
<summary>Click for details</summary>

Auto-configuration ordering coordinates **when configuration definitions are processed**. It does not dictate the order in which resulting bean instances are created.

~~~text
auto-configuration A processed before B
→ B can reason about definitions contributed by A

bean X depends on bean Y
→ ordinary Spring dependency/lifecycle rules determine creation order
~~~

Confusing these layers leads to fragile designs. If one bean truly depends on another bean, express that dependency through injection or the appropriate container relationship rather than relying on auto-configuration order.

</details>

- [Back to top](#back-to-top)

---

## <a id="before-after-ordering">Before and After Relationships</a>

<details>
<summary>Click for details</summary>

When one auto-configuration must be processed before or after another, Spring Boot supports explicit relationships such as the before/after attributes on AutoConfiguration and the AutoConfigureBefore/AutoConfigureAfter annotations.

Example intent:

~~~text
AcmeCoreAutoConfiguration
        ↓ before
AcmeMetricsAutoConfiguration
~~~

The metrics configuration can then assume the core definitions had an opportunity to be registered first. A name-based relationship is useful when the other class should not become a hard classpath dependency.

Use ordering for real definition-processing relationships, not merely to make a list look tidy.

</details>

- [Back to top](#back-to-top)

---

## <a id="auto-configure-order">Ordering Independent Auto-configurations</a>

<details>
<summary>Click for details</summary>

AutoConfigureOrder provides an ordering value for auto-configurations that need relative ordering without a direct before/after relationship.

It is conceptually similar to ordered processing, but it belongs specifically to the auto-configuration selection pipeline. Do not assume that applying an order value to a configuration also orders arbitrary runtime callbacks, filters, listeners, or bean creation.

Prefer direct before/after relationships when a concrete dependency exists because they document the relationship. Use broad numeric ordering only when the configurations truly need a general position in the sequence.

</details>

- [Back to top](#back-to-top)

---

## <a id="conditional-composition">Composing Conditional Configuration</a>

<details>
<summary>Click for details</summary>

Large integrations are easier to reason about when configuration is decomposed around applicability boundaries. One top-level auto-configuration can import or contain narrower conditional configurations for optional features.

For example:

~~~text
AcmeClientAutoConfiguration
├── core client configuration
├── metrics configuration      [metrics library present]
└── servlet configuration      [servlet web application]
~~~

This keeps conditions close to the feature they protect. It also reduces the risk that one optional feature disables or breaks the whole integration.

Composition should remain explicit: use imports and focused configuration classes rather than a broad component scan.

</details>

- [Back to top](#back-to-top)

---

## <a id="optional-technology-isolation">Isolating Optional Technology</a>

<details>
<summary>Click for details</summary>

Optional technology must be isolated at both dependency and class-loading boundaries. A configuration that mentions an optional type too early can fail before its condition has a chance to say “not applicable.”

Practical pattern:

~~~text
top-level auto-configuration
        ↓ imports
nested/separate optional configuration
        ↓ protected by class-level ConditionalOnClass
Bean methods that safely reference optional types
~~~

This structure also makes testing easier: use a FilteredClassLoader to remove the optional library and prove that only the optional branch disappears.

The trade-off is more configuration classes, but the benefit is a clean failure boundary and a starter that can support optional capabilities without forcing all of them onto every consumer.

Ordering and isolation reduce ambiguity, but production failures still require evidence about why a candidate matched or did not match. The next chapter focuses on exclusions and condition diagnostics.

</details>

- [Back to top](#back-to-top)
