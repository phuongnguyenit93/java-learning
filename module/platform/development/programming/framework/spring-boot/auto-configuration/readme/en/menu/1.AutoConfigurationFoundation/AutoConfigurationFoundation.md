<a id="back-to-top"></a>

# Auto-configuration Foundation

## Menu
- [What Is Auto-configuration and Why Does Spring Boot Need It?](#auto-configuration-what-why)
- [Spring Framework Configuration vs Spring Boot Auto-configuration](#boot-vs-framework-configuration)
- [Defaults, Back-off, and Application Control](#defaults-and-back-off-contract)
- [End-to-end Auto-configuration Flow](#auto-configuration-end-to-end-flow)

## <a id="auto-configuration-what-why">What Is Auto-configuration and Why Does Spring Boot Need It?</a>

<details>
<summary>Click for details</summary>

Auto-configuration is Spring Boot's mechanism for adding configuration that is likely to be useful without requiring the application to declare every infrastructure bean explicitly. It examines facts around the application—classes on the classpath, properties, existing beans, resources, and application type—and contributes configuration only when its conditions match.

The concrete problem is repetition. Without auto-configuration, applications using the same library repeatedly create the same infrastructure beans, copy the same defaults, and wire the same collaborators. Boot turns that repeated setup into reusable conditional configuration.

~~~text
application dependencies + configuration + user beans
                        ↓
             auto-configuration candidates
                        ↓
                 condition evaluation
                        ↓
             matching default configuration
~~~

Auto-configuration is not random guessing. It is ordinary Spring configuration selected by explicit rules. A useful first piece of evidence is the Condition Evaluation Report: it shows which candidates matched and which did not.

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-vs-framework-configuration">Spring Framework Configuration vs Spring Boot Auto-configuration</a>

<details>
<summary>Click for details</summary>

Spring Framework owns the underlying container mechanisms: Configuration classes, Bean methods, ApplicationContext, bean definitions, imports, and conditions. Spring Boot builds on those mechanisms and adds a convention-driven library of reusable configuration plus discovery rules.

For example, a plain Spring application can define an AcmeClient explicitly. A Boot integration can instead publish an auto-configuration that contributes an AcmeClient only when the Acme library is present, suitable configuration exists, and the application has not already supplied its own client.

~~~text
How Spring creates and manages beans
→ Spring Framework

When Boot decides reusable default configuration should participate
→ Spring Boot auto-configuration
~~~

This module therefore focuses on Boot's selection, back-off, ordering, diagnostics, and authoring contracts rather than re-teaching the Spring container.

</details>

- [Back to top](#back-to-top)

---

## <a id="defaults-and-back-off-contract">Defaults, Back-off, and Application Control</a>

<details>
<summary>Click for details</summary>

Useful auto-configuration provides defaults, not a trap. A library integration normally offers a convenient default when the application has not made a more specific decision. If the application already defines the relevant bean, the auto-configuration commonly backs off.

~~~text
no application AcmeClient
→ Boot may provide the default AcmeClient

application already provides AcmeClient
→ Boot leaves that decision alone
~~~

Back-off is broader than one annotation. Property switches, missing classes, application type, resources, or explicit exclusions can all prevent an auto-configuration from contributing configuration. The design goal is the same: make the common path easy while keeping the application in control.

This is why “auto-configured” never means “unconditionally created.” A default only makes sense together with the conditions that guard it.

</details>

- [Back to top](#back-to-top)

---

## <a id="auto-configuration-end-to-end-flow">End-to-end Auto-configuration Flow</a>

<details>
<summary>Click for details</summary>

An end-to-end startup can be understood as a sequence:

~~~text
1. SpringBootApplication enables auto-configuration
        ↓
2. Boot discovers candidate auto-configuration classes
        ↓
3. candidates are ordered
        ↓
4. conditions inspect current application state
        ↓
5. matching configuration contributes bean definitions
        ↓
6. back-off rules let explicit application choices win
        ↓
7. the container later creates beans from the resulting definitions
~~~

Two separations are essential. Candidate discovery is not condition matching: a class can be discovered and still not apply. Configuration ordering is also not bean-instantiation ordering: ordering coordinates configuration processing, while actual bean creation still follows normal container dependencies and lifecycle rules.

The rest of the module expands each stage of this flow. When a later detail is confusing, ask which stage owns the behavior.

</details>

- [Back to top](#back-to-top)
