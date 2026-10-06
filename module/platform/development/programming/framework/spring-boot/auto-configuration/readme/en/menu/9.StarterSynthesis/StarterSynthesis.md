<a id="back-to-top"></a>

# Starter Design and Synthesis

## Menu
- [What a Starter Adds to Auto-configuration](#starter-purpose)
- [Separate Autoconfigure and Starter Modules vs a Combined Starter](#starter-split-or-combine)
- [Starter Naming and Package Ownership](#starter-naming)
- [Configuration-key Namespace and Metadata](#configuration-key-namespace)
- [Dependency Opinion and Optional Features](#starter-dependency-opinion)
- [From Starter Dependency to Configured Application](#starter-end-to-end-flow)
- [Boundaries and Next Learning Steps](#auto-configuration-boundaries-next)

## <a id="starter-purpose">What a Starter Adds to Auto-configuration</a>

<details>
<summary>Click for details</summary>

Auto-configuration contains conditional configuration logic. A starter exists mainly to make the dependency choice convenient: adding one dependency should provide the typical pieces needed to use the integration.

Spring Boot's guidance describes a starter as an opinionated view of the dependencies required to get started. The starter does not need business logic merely to justify its existence.

~~~text
starter dependency
→ brings Boot core + Acme integration dependencies
→ exposes Acme auto-configuration candidate
→ Boot evaluates candidate conditions
→ application gets a sensible default setup
~~~

This is why starter design belongs at the end of the module: the starter is useful because it packages the conditions, back-off, and defaults already understood.

</details>

- [Back to top](#back-to-top)

---

## <a id="starter-split-or-combine">Separate Autoconfigure and Starter Modules vs a Combined Starter</a>

<details>
<summary>Click for details</summary>

A common library layout separates the auto-configuration code from the starter:

~~~text
acme-spring-boot
→ auto-configuration + configuration properties + extension APIs

acme-spring-boot-starter
→ dependency opinion for the common setup
~~~

The split is not mandatory. Spring Boot explicitly allows a straightforward integration without meaningful optional features to combine both roles in one starter module.

Separate modules are more useful when optional dependencies or multiple starter opinions matter. A consumer can then use the autoconfigure artifact without accepting the starter's entire dependency opinion.

Choose the structure from extension and dependency needs, not from a rule that every starter must be two modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="starter-naming">Starter Naming and Package Ownership</a>

<details>
<summary>Click for details</summary>

Third-party starter names should use a namespace owned by the library rather than pretending to be an official Spring Boot module. Boot's guidance reserves the spring-boot naming space for official support.

A typical third-party pattern is:

~~~text
acme-spring-boot
acme-spring-boot-starter
~~~

If the integration is combined into one artifact, naming it as the starter communicates that one dependency is the intended entry point.

Package ownership should be equally clear. Auto-configuration classes belong under the library's package, not under the consumer application's package. Stable naming reduces surprises for ordering and exclusion references.

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-key-namespace">Configuration-key Namespace and Metadata</a>

<details>
<summary>Click for details</summary>

Configuration keys exposed by a starter should use a namespace owned by the library, such as acme.client. Third-party integrations should not place their keys inside Boot-owned namespaces such as server, management, or spring.

~~~text
acme.client.endpoint
acme.client.timeout
acme.client.enabled
~~~

The properties should be documented so configuration metadata can provide useful IDE assistance. The author should inspect the generated metadata and verify that descriptions and types match the public configuration contract.

Property binding mechanics remain the responsibility of Externalized Configuration; starter design is responsible for exposing a coherent, collision-resistant namespace.

</details>

- [Back to top](#back-to-top)

---

## <a id="starter-dependency-opinion">Dependency Opinion and Optional Features</a>

<details>
<summary>Click for details</summary>

A starter expresses a dependency opinion: the set of libraries most users should receive by default. It should include what is normally required, while avoiding unnecessary optional technologies.

If an integration has many optional features, separate the auto-configuration from the starter so consumers can choose a different dependency opinion. Multiple starters can even target different common combinations while reusing the same auto-configuration layer.

The important distinction is:

~~~text
auto-configuration
→ conditional behavior

starter
→ dependency convenience/opinion
~~~

Mixing the two concepts leads to poor decisions such as forcing every optional library onto every consumer just because one condition exists for it.

</details>

- [Back to top](#back-to-top)

---

## <a id="starter-end-to-end-flow">From Starter Dependency to Configured Application</a>

<details>
<summary>Click for details</summary>

The complete learner mental model is now:

~~~text
application adds starter
        ↓
starter supplies typical dependencies
        ↓
dependency JAR exposes AutoConfiguration.imports
        ↓
Boot discovers auto-configuration candidates
        ↓
ordering coordinates configuration processing
        ↓
conditions evaluate classpath/properties/beans/context
        ↓
matching defaults contribute bean definitions
        ↓
user choices cause back-off where designed
        ↓
Condition Evaluation Report explains the decision
        ↓
focused context tests prove the contract
~~~

This flow connects dependency design to runtime behavior without confusing the starter with the mechanism that creates beans.

</details>

- [Back to top](#back-to-top)

---

## <a id="auto-configuration-boundaries-next">Boundaries and Next Learning Steps</a>

<details>
<summary>Click for details</summary>

This module ends at the Spring Boot auto-configuration boundary.

Continue elsewhere when the question changes:

- “How does the Spring container create and manage these beans?” → Spring Framework core container.
- “How are properties loaded, ordered, bound, and validated?” → Spring Boot Externalized Configuration.
- “How should the whole Boot application be tested?” → Spring Boot Testing.
- “How do Gradle/Maven plugins, BOMs, executable archives, or container images work?” → Build Tooling and Packaging.
- “How does AOT/native-image processing affect this configuration?” → Native Image.

The durable mental model to carry forward is: **starter dependencies make capabilities available; candidate discovery finds integrations; conditions select what is appropriate; back-off preserves application control; diagnostics and focused tests make the decision observable.**

</details>

- [Back to top](#back-to-top)
