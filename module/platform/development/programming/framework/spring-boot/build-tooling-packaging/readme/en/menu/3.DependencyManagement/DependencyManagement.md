<a id="back-to-top"></a>

# Dependency Management and the Spring Boot BOM

## Menu
- [What Is `spring-boot-dependencies`?](#spring-boot-dependencies)
- [How Do the Maven Parent and BOM Import Differ?](#maven-parent-vs-bom)
- [How Does Gradle Consume Boot-Managed Dependency Versions?](#gradle-dependency-alignment)
- [What Responsibility Do You Take On When Overriding a Managed Version?](#managed-version-override)

## <a id="spring-boot-dependencies">What Is `spring-boot-dependencies`?</a>

<details>
<summary>Click for details</summary>

`spring-boot-dependencies` is Spring Boot's curated dependency-management BOM. It records a tested baseline of versions for Spring projects and many commonly used third-party libraries. Using that baseline lets application builds omit many individual version declarations while keeping a set that Boot expects to work together.

A BOM does **not** add every managed library to the application. It supplies version constraints/management for dependencies that the build actually declares. Starters may bring dependencies transitively, while the BOM answers a different question: *which version should be selected?*

The version baseline is tied to a Boot release line. Using Boot 3.3.13's BOM means the build consumes the dependency versions curated and tested for that Boot release, even when the individual libraries have newer versions available. The goal is compatibility across the set, not always choosing the newest artifact independently.

Dependency management therefore reduces the number of version decisions in an application build, but it does not eliminate dependency resolution. Exclusions, optional/transitive relationships, repository availability, Gradle conflict resolution, and Maven mediation still belong to the underlying build system.

### References

- [Spring Boot 3.3 — Using the Maven Plugin](https://docs.spring.io/spring-boot/3.3/maven-plugin/using.html)
- [Spring Boot 3.3 — Managing Dependencies with Gradle](https://docs.spring.io/spring-boot/3.3/gradle-plugin/managing-dependencies.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="maven-parent-vs-bom">How Do the Maven Parent and BOM Import Differ?</a>

<details>
<summary>Click for details</summary>

Using `spring-boot-starter-parent` gives a Maven project more than dependency versions. The parent inherits Boot's dependency management and also supplies useful Maven defaults and plugin configuration. It is convenient when the project can adopt Boot's parent POM.

Importing `spring-boot-dependencies` in `<dependencyManagement>` is narrower. It brings the managed dependency versions while allowing the project to keep a different parent. That flexibility also means the build does not automatically inherit all the other parent-level plugin/default configuration.

So the decision is not “two equivalent spellings.” It is **full parent convention versus BOM-only dependency management**.

</details>

- [Back to top](#back-to-top)

---

## <a id="gradle-dependency-alignment">How Does Gradle Consume Boot-Managed Dependency Versions?</a>

<details>
<summary>Click for details</summary>

Gradle can consume Boot's managed versions in more than one supported style. A common path is to use the `io.spring.dependency-management` plugin; when used with Spring Boot's Gradle plugin, Boot's BOM can be imported into that dependency-management model. Another path is Gradle's native platform support, for example importing the `spring-boot-dependencies` coordinates as a platform.

The important concept is independent of syntax:

```text
Boot BOM
→ version alignment input
→ Gradle resolution selects compatible versions
→ application declares the dependencies it actually uses
```

Do not confuse applying the Boot plugin with learning all of Gradle's version-catalog, platform, constraint, or resolution semantics; those remain Gradle topics.

The two Gradle paths have an important practical difference in Boot 3.3. The io.spring.dependency-management plugin automatically imports the Boot BOM when used with the Boot plugin and supports property-based customization of managed versions. Gradle's native BOM support can instead declare spring-boot-dependencies as a platform or enforcedPlatform; this stays closer to Gradle's native model and is generally the faster path.

A platform treats BOM versions as recommendations that may be influenced by other constraints, while enforcedPlatform makes them requirements for the configurations that consume it. That distinction is Gradle semantics, but it affects how strongly the Boot baseline is applied. The Boot module should explain the consequence without becoming a full lesson on Gradle resolution.

</details>

- [Back to top](#back-to-top)

---

## <a id="managed-version-override">What Responsibility Do You Take On When Overriding a Managed Version?</a>

<details>
<summary>Click for details</summary>

Boot-managed versions are a compatibility baseline, not a prohibition. A project can override a version when it has a real requirement, such as a security fix or a feature that is not yet in the baseline. The cost is that the project now owns more compatibility verification.

Before overriding, ask:

```text
Why is the override needed?
→ Is the new version compatible with the Boot 3.3 baseline?
→ Does it require related libraries to move together?
→ Are auto-configuration assumptions still valid?
→ Do integration tests cover the affected path?
```

Randomly “upgrading the one dependency that looks old” can break a dependency family that Boot intentionally manages as a coherent set. Prefer the managed baseline unless there is evidence for a deliberate deviation.

When using the dependency-management plugin, many Boot-managed families expose version properties that can be overridden as one coordinated value. When using Gradle's native BOM support, those Maven-style properties are not the customization mechanism; use Gradle's own constraints or resolution mechanisms instead. The override technique therefore depends on the dependency-management path.

Whichever mechanism is used, record the reason and the expected removal condition for a deviation. A temporary CVE override that remains after the next compatible Boot release can silently become permanent maintenance debt.

</details>

- [Back to top](#back-to-top)
