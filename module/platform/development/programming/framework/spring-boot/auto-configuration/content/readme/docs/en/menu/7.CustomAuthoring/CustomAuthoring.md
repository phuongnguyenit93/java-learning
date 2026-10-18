<a id="back-to-top"></a>

# Authoring Custom Auto-configuration

## Menu
- [@AutoConfiguration and the Authoring Contract](#auto-configuration-class-contract)
- [Registration, Package Boundaries, and Explicit Imports](#registration-and-package-boundaries)
- [Integrating @ConfigurationProperties](#configuration-properties-integration)
- [Designing Optional Dependencies](#optional-dependency-design)
- [Auto-configuration Processor and Condition Metadata](#auto-configure-metadata)
- [Stable Class Identities for Ordering and Exclusion](#stable-auto-configuration-identities)

## <a id="auto-configuration-class-contract">@AutoConfiguration and the Authoring Contract</a>

<details>
<summary>Click for details</summary>

AutoConfiguration marks a configuration class as a Spring Boot auto-configuration candidate. It is still Spring configuration, but Boot treats it as part of the auto-configuration selection pipeline and its proxyBeanMethods behavior is fixed to false.

A good auto-configuration class is narrow: it declares the conditions under which one integration is valid and contributes only the definitions that integration owns.

~~~java
@AutoConfiguration
@ConditionalOnClass(AcmeClient.class)
@EnableConfigurationProperties(AcmeProperties.class)
class AcmeClientAutoConfiguration {
}
~~~

The annotation alone does not publish the class. The candidate must also be listed in AutoConfiguration.imports. Conditions and back-off rules then determine whether it contributes anything in a particular application.

</details>

- [Back to top](#back-to-top)

---

## <a id="registration-and-package-boundaries">Registration, Package Boundaries, and Explicit Imports</a>

<details>
<summary>Click for details</summary>

Library auto-configuration should use a package owned by the library, be registered in AutoConfiguration.imports, and avoid relying on component scanning. Supporting configuration should be imported explicitly.

~~~text
com.acme.boot.autoconfigure
├── AcmeClientAutoConfiguration
├── AcmeMetricsConfiguration
└── AcmeProperties
~~~

The package name is not the consumer application's scan root. That independence is part of the reusable-library contract.

Explicit imports also make optional branches auditable: a reader can see which configuration belongs to the auto-configuration instead of discovering hidden components through a scan.

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-properties-integration">Integrating @ConfigurationProperties</a>

<details>
<summary>Click for details</summary>

ConfigurationProperties is a natural boundary between externalized application configuration and auto-configuration decisions. The properties type owns a structured set of configuration values; the auto-configuration consumes those values to construct or customize infrastructure.

~~~java
@ConfigurationProperties("acme.client")
public class AcmeProperties {
    private URI endpoint;
    private Duration timeout = Duration.ofSeconds(2);
    // accessors
}
~~~

The auto-configuration can enable that properties type and inject it into Bean methods. Conditions may also use specific properties to decide whether a feature is enabled.

Keep ownership clear: property loading, precedence, relaxed binding, and validation semantics are taught in Externalized Configuration. Here the focus is how a custom auto-configuration exposes and consumes a stable configuration contract.

</details>

- [Back to top](#back-to-top)

---

## <a id="optional-dependency-design">Designing Optional Dependencies</a>

<details>
<summary>Click for details</summary>

An autoconfigure artifact is easiest to reuse when technology dependencies that represent optional features remain optional. The auto-configuration can then check for those technologies rather than forcing all of them onto every consumer.

Example design:

~~~text
acme-spring-boot
├── core Boot/autoconfigure APIs
├── optional Acme client library
└── optional metrics integration

acme-spring-boot-starter
└── chooses the typical dependency set for most consumers
~~~

This separation lets advanced consumers depend only on the autoconfigure artifact and choose their own optional libraries, while the starter offers a convenient opinion.

Optional dependency design must be paired with condition and class-loading isolation. Declaring a dependency optional is not enough if the configuration eagerly links to a missing type.

</details>

- [Back to top](#back-to-top)

---

## <a id="auto-configure-metadata">Auto-configuration Processor and Condition Metadata</a>

<details>
<summary>Click for details</summary>

Spring Boot provides an auto-configuration annotation processor that can generate META-INF/spring-autoconfigure-metadata.properties. The metadata lets Boot filter some obviously nonmatching candidates earlier, reducing startup work.

This is an optimization layer, not a second condition language. The actual Conditional annotations still express the applicability semantics of the auto-configuration.

~~~text
source annotations
→ annotation processor
→ spring-autoconfigure-metadata.properties
→ earlier candidate filtering where metadata is sufficient
→ normal condition semantics remain authoritative
~~~

Do not design behavior that is only “correct” when metadata is generated. The metadata improves selection efficiency; it should not change the intended result.

</details>

- [Back to top](#back-to-top)

---

## <a id="stable-auto-configuration-identities">Stable Class Identities for Ordering and Exclusion</a>

<details>
<summary>Click for details</summary>

Other auto-configurations and applications can refer to an auto-configuration class by type or by class name for ordering and exclusion. That makes the class identity part of a public integration contract even if applications rarely instantiate it directly.

In Spring Boot 3.3 there is no general AutoConfiguration.replacements mechanism for transparently remapping an old auto-configuration class identity. Renaming or moving a published auto-configuration therefore requires compatibility planning rather than assuming Boot will repair all references.

Practical rule: choose stable package and class names, especially for public starter integrations. If a breaking move is unavoidable, consider the effect on before/after references, exclude/excludeName usage, documentation, and consumer configuration.

This is a version boundary: later Boot lines add capabilities that are not part of the 3.3 baseline used by this repository.

An authored auto-configuration is only trustworthy when its positive matches, negative matches, and back-off behavior are executable and repeatable. The next chapter builds that verification matrix with focused context runners.

</details>

- [Back to top](#back-to-top)
