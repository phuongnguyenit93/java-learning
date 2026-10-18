<a id="back-to-top"></a>

# Starters, Managed Dependencies, and the Classpath

## Menu
- [What Is a Spring Boot Starter and Why Use One?](#starter-purpose)
- [What Problem Do Managed Dependency Versions Solve?](#managed-dependencies)
- [How Can the Classpath Change Boot Behavior?](#classpath-driven-behavior)
- [Why Is a Starter Not the Same as Auto-Configuration?](#starter-vs-auto-configuration)
- [When Should You Use a Starter, an Individual Dependency, or an Override?](#dependency-choice-boundary)

## <a id="starter-purpose">What Is a Spring Boot Starter and Why Use One?</a>

<details>
<summary>Click for details</summary>

A Spring Boot starter is a dependency descriptor that collects a useful set of libraries for a common application capability. Instead of discovering and declaring every Spring and third-party library individually, you declare a starter whose dependency set represents a supported starting point.

For example, a web starter can bring in Spring's web stack plus the supporting libraries Boot expects for that use case. The starter itself does not "run" the web application. Its main job is to shape the classpath conveniently.

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

This matters because the classpath becomes input to other Boot mechanisms. Once relevant classes are present, auto-configuration can recognize that capability and configure matching infrastructure when its other conditions are also satisfied.

Starters are therefore about **dependency intent and convenience**. They reduce library-selection work and give a project a conventional, tested dependency baseline.

</details>

- [Back to top](#back-to-top)

---

## <a id="managed-dependencies">What Problem Do Managed Dependency Versions Solve?</a>

<details>
<summary>Click for details</summary>

A real application usually depends on many libraries that must work together. Choosing each version independently can create incompatible combinations: a newer library may expect an API version that another dependency does not provide, or two transitive dependency trees may disagree about which version should win.

Each Spring Boot release publishes a curated set of supported dependency versions. When a supported build setup consumes Boot's dependency management, many common dependencies can be declared without repeating version numbers. Upgrading the Boot line then moves a coordinated set of versions together.

```text
Boot version
    ↓
curated dependency set
    ↓
Spring libraries + selected third-party libraries
```

Managed does not mean immutable. Versions can be overridden when there is a justified need, but an override moves part of the compatibility responsibility back to the application team. The exact Maven/Gradle BOM and plugin mechanics belong to `build-tooling-packaging`.

### References

- [Spring Boot 3.3 — Build Systems](https://docs.spring.io/spring-boot/3.3/reference/using/build-systems.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="classpath-driven-behavior">How Can the Classpath Change Boot Behavior?</a>

<details>
<summary>Click for details</summary>

The classpath is more than a list of code that can be imported. To Boot, it is also evidence about which technologies are available. Adding or removing a library can therefore change what Boot is capable of configuring.

Suppose an application begins as a non-web process. Adding the usual Servlet web stack makes Spring MVC classes and an embedded server implementation available. By default, `SpringApplication` uses classpath evidence to determine the `WebApplicationType` and creates the corresponding application context. Auto-configuration then contributes and configures matching infrastructure when its conditions are satisfied, such as the embedded Servlet web server. Removing those classpath capabilities can therefore change the inferred application type and the infrastructure that Boot configures.

```text
dependency declaration
        ↓
classpath contents change
        ↓
Boot can detect different available capabilities
        ↓
conditional configuration may change
```

Classpath presence alone is not the complete rule for auto-configuration. Properties, existing beans, and other conditions can also matter. Also keep the responsibilities separate: `SpringApplication` determines the application type and context; auto-configuration contributes matching configuration and infrastructure inside that context. The point here is simply that dependency choices affect runtime possibilities.

</details>

- [Back to top](#back-to-top)

---

## <a id="starter-vs-auto-configuration">Why Is a Starter Not the Same as Auto-Configuration?</a>

<details>
<summary>Click for details</summary>

A starter and an auto-configuration solve different problems even though they often appear together.

| Starter | Auto-configuration |
| --- | --- |
| build-time dependency convenience | runtime/application-context configuration mechanism |
| puts a curated set of libraries on the classpath | contributes configuration when conditions match |
| represented by dependency metadata | represented by Boot configuration classes and conditions |

For example, declaring `spring-boot-starter-web` gives the application a conventional web classpath. Boot's web auto-configuration can then observe that classpath and decide what beans and integrations are appropriate. The starter did not register those beans itself.

This distinction is useful when debugging: "is the library available?" is a classpath/dependency question; "why did Boot create or skip this configuration?" is an auto-configuration question.

</details>

- [Back to top](#back-to-top)

---

## <a id="dependency-choice-boundary">When Should You Use a Starter, an Individual Dependency, or an Override?</a>

<details>
<summary>Click for details</summary>

Prefer a Boot starter when it accurately describes a capability your application needs and you want the conventional dependency set. Use an individual dependency when you need a narrow library and do not want the wider starter set. Override a managed version only when you have a concrete compatibility, security, or feature requirement and have verified the resulting combination.

The choice can be summarized as:

```text
common Boot use case → starter
narrow library need  → individual dependency
exception to managed baseline → explicit version override + verification
```

Fundamentals focuses on the decision model. How Gradle or Maven imports Boot's BOM, how the Boot build plugin changes tasks, and how executable artifacts are produced belong to `build-tooling-packaging`.

</details>

- [Back to top](#back-to-top)
