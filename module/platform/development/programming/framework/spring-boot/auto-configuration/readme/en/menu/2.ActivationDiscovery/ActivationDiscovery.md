<a id="back-to-top"></a>

# Activation and Candidate Discovery

## Menu
- [@SpringBootApplication and @EnableAutoConfiguration](#spring-boot-application-and-enable-auto-configuration)
- [Candidate Discovery Is Not Bean Creation](#candidate-discovery-vs-bean-creation)
- [AutoConfiguration.imports and ImportCandidates](#auto-configuration-imports)
- [Auto-configuration Packages, Component Scanning, and Explicit Imports](#auto-configuration-package-isolation)
- [Application Base Package vs Auto-configuration Discovery](#application-base-package-boundary)

## <a id="spring-boot-application-and-enable-auto-configuration">@SpringBootApplication and @EnableAutoConfiguration</a>

<details>
<summary>Click for details</summary>

SpringBootApplication includes EnableAutoConfiguration, so a normal Boot application already enables the auto-configuration selection machinery. Adding EnableAutoConfiguration again does not create a second mechanism.

The same SpringBootApplication annotation also enables application component scanning, but that is a different concern. Component scanning discovers application components. Auto-configuration selection discovers published auto-configuration candidates from dependencies.

The distinction matters because a library's auto-configuration should remain discoverable even when it lives outside the application's base package.

</details>

- [Back to top](#back-to-top)

---

## <a id="candidate-discovery-vs-bean-creation">Candidate Discovery Is Not Bean Creation</a>

<details>
<summary>Click for details</summary>

Boot must first find the auto-configuration classes that are available for consideration. Discovery answers “which candidates exist?” Condition evaluation answers “which of those candidates apply here?”

A discovered candidate can still contribute nothing because a required class is absent, a property disables it, a required bean is missing, a user bean causes back-off, or the candidate was explicitly excluded.

When debugging a missing bean, separate these questions:

~~~text
Was the auto-configuration discovered?
        ↓
Did its configuration-level conditions match?
        ↓
Did its bean-level conditions match?
        ↓
Was it excluded or overridden?
~~~

This prevents a common mistake: treating “not applied” as proof that the candidate was never discovered.

</details>

- [Back to top](#back-to-top)

---

## <a id="auto-configuration-imports">AutoConfiguration.imports and ImportCandidates</a>

<details>
<summary>Click for details</summary>

Spring Boot 3.3 discovers published auto-configuration candidates from:

~~~text
META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
~~~

Each non-comment line names an auto-configuration class. Boot's import-candidate infrastructure reads those declarations when auto-configuration is enabled.

For an Acme integration the file might contain:

~~~text
com.acme.boot.AcmeClientAutoConfiguration
com.acme.boot.AcmeMetricsAutoConfiguration
~~~

The imports file is a discovery index, not a guarantee that the classes apply. Their conditions are evaluated against the consuming application afterward.

</details>

- [Back to top](#back-to-top)

---

## <a id="auto-configuration-package-isolation">Auto-configuration Packages, Component Scanning, and Explicit Imports</a>

<details>
<summary>Click for details</summary>

Published auto-configuration should live in a package space owned by the library and be loaded through AutoConfiguration.imports. Spring Boot's guidance deliberately avoids component scanning as the discovery mechanism for auto-configuration.

The auto-configuration should also avoid enabling broad component scanning to find its own helpers. Import supporting configuration deliberately instead:

~~~java
@AutoConfiguration
@Import(AcmeClientConfiguration.class)
class AcmeClientAutoConfiguration {
}
~~~

This keeps the integration boundary explicit and avoids accidentally scanning application classes or unrelated library internals.

Normal component scanning in the consumer application remains valid. The restriction applies to how the published auto-configuration itself is located and how it pulls in components that it owns.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-base-package-boundary">Application Base Package vs Auto-configuration Discovery</a>

<details>
<summary>Click for details</summary>

The package of the class annotated with EnableAutoConfiguration—normally the SpringBootApplication class—has meaning as an application-side default package. Other Boot features may use it when determining where application classes should be searched.

That is separate from external auto-configuration discovery:

~~~text
application base package
→ application-side scanning conventions

AutoConfiguration.imports in dependency JARs
→ external auto-configuration candidates
~~~

Therefore com.acme.boot.AcmeClientAutoConfiguration does not need to live below com.example.myapp. Moving the application's main class can change application scanning boundaries, but should not be necessary to make a correctly published external auto-configuration visible.

Once candidate discovery is separated from application scanning, the next question is selection: which discovered candidates should actually apply? The condition model answers that question.

</details>

- [Back to top](#back-to-top)
