<a id="back-to-top"></a>

# Condition-driven Selection

## Menu
- [Why Auto-configuration Needs Conditions](#condition-model-purpose)
- [Classpath Conditions](#class-conditions)
- [Bean Conditions and Evaluation Timing](#bean-conditions-and-timing)
- [Property Conditions and Configuration Inputs](#property-conditions)
- [Resource and Web-application Conditions](#resource-and-web-conditions)
- [Optional Classes, Annotation Metadata, and JVM Linkage Boundaries](#optional-class-linkage)

## <a id="condition-model-purpose">Why Auto-configuration Needs Conditions</a>

<details>
<summary>Click for details</summary>

Reusable configuration cannot assume that every consumer has the same libraries, properties, beans, resources, or runtime type. Conditions make auto-configuration context-sensitive.

Without conditions, simply adding an integration JAR could force unwanted beans into every application. With conditions, the integration states its applicability contract explicitly:

~~~text
required library present?
feature enabled?
required collaborator present?
right application type?
required resource present?
        ↓
only then contribute configuration
~~~

Conditions are therefore more than an optimization. They define when the default configuration is valid.

</details>

- [Back to top](#back-to-top)

---

## <a id="class-conditions">Classpath Conditions</a>

<details>
<summary>Click for details</summary>

Class conditions such as ConditionalOnClass and ConditionalOnMissingClass make classpath state part of the decision. They are useful for integrations that only make sense when a particular technology is available.

~~~java
@AutoConfiguration
@ConditionalOnClass(AcmeClient.class)
class AcmeClientAutoConfiguration {
}
~~~

If AcmeClient is absent, the candidate should not try to configure an Acme client. A starter often provides the dependency; the class condition then allows the corresponding configuration to participate.

At configuration-class level, Boot can inspect annotation metadata without eagerly loading every optional type. Later we distinguish that safe metadata inspection from references that still force JVM linkage.

</details>

- [Back to top](#back-to-top)

---

## <a id="bean-conditions-and-timing">Bean Conditions and Evaluation Timing</a>

<details>
<summary>Click for details</summary>

Bean conditions such as ConditionalOnBean and ConditionalOnMissingBean reason about bean definitions known to the ApplicationContext. They power many back-off rules.

Timing matters because these conditions see definitions processed so far. Boot applies auto-configuration after user-defined bean definitions, which is why missing-bean conditions are especially useful there.

~~~java
@Bean
@ConditionalOnMissingBean
AcmeClient acmeClient(AcmeProperties properties) {
    return new AcmeClient(properties.getEndpoint());
}
~~~

The target type matters as well. A condition on a Bean method can infer its target from the declared return type. If a method exposes an overly broad type while another condition searches for a concrete type, the result may differ from the author's mental model.

The evidence should be tested both ways: no user bean means the default appears; a user bean means the default backs off.

One special timing caveat applies to ConditionalOnExpression. If a SpEL expression references a bean, that bean is initialized very early during context refresh and is not eligible for normal post-processing such as configuration-properties binding. Its state can therefore be incomplete. Prefer conditions with explicit classpath, property, or bean contracts instead of using a bean reference in an expression to force an early lookup.

</details>

- [Back to top](#back-to-top)

---

## <a id="property-conditions">Property Conditions and Configuration Inputs</a>

<details>
<summary>Click for details</summary>

ConditionalOnProperty lets environment values influence selection. This connects auto-configuration to Externalized Configuration without making this module the owner of property-source precedence or binding.

~~~java
@ConditionalOnProperty(
    prefix = "acme.client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
~~~

This example means the feature is enabled unless the application explicitly disables it. Other designs can require a property to be present or to equal a particular value.

The boundary is deliberate: this module explains how a property changes the auto-configuration decision. How Boot loads, orders, binds, and validates configuration values belongs to Externalized Configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="resource-and-web-conditions">Resource and Web-application Conditions</a>

<details>
<summary>Click for details</summary>

Conditions can inspect more than classes, beans, and properties. ConditionalOnResource can require a resource. ConditionalOnWebApplication and ConditionalOnNotWebApplication let configuration specialize for a web or non-web application.

That supports clean decomposition:

~~~text
core Acme client configuration
        +
servlet-specific integration
        +
reactive-specific integration
~~~

The core configuration does not need to assume every consumer runs a web stack. Web-specific configuration can be activated only in the appropriate context.

This module owns selection by application type; MVC/WebFlux mechanics remain with Spring Framework and Spring Boot Web Runtime.

</details>

- [Back to top](#back-to-top)

---

## <a id="optional-class-linkage">Optional Classes, Annotation Metadata, and JVM Linkage Boundaries</a>

<details>
<summary>Click for details</summary>

Class-level conditions are often inspected from annotation metadata before an optional class is loaded. That protection does not automatically extend to every reference in the configuration class.

Consider a Bean method whose return type comes from an optional dependency. The JVM may need to resolve that method signature while loading the containing configuration class, before a method-level condition can protect it.

Isolate optional types behind a configuration class protected by a class-level condition:

~~~java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OptionalLibrary.class)
static class OptionalLibraryConfiguration {

    @Bean
    OptionalLibraryAdapter adapter() {
        return new OptionalLibraryAdapter();
    }
}
~~~

When composing a custom meta-annotation around ConditionalOnClass, using the class name rather than a direct class literal can also be necessary because composed annotation metadata does not receive every special handling path.

A FilteredClassLoader test provides useful evidence: remove the optional library and verify that the related configuration disappears cleanly instead of failing with a linkage error.

Conditions decide whether configuration is applicable; they do not by themselves define how application choices override Boot defaults. The next chapter turns condition results into an explicit back-off and user-control contract.

</details>

- [Back to top](#back-to-top)
