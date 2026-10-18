<a id="back-to-top"></a>

# Spring Boot Gradle and Maven Plugins

## Menu
- [What Do the Spring Boot Gradle and Maven Plugins Add?](#boot-build-plugins)
- [Which Run, Package, and Image Tasks Belong to the Boot Plugins?](#plugin-responsibilities)
- [What Remains the Responsibility of Gradle or Maven?](#plugin-vs-build-tool)
- [How Should You Reason About the Gradle and Maven Integration Paths?](#plugin-choice-boundary)

## <a id="boot-build-plugins">What Do the Spring Boot Gradle and Maven Plugins Add?</a>

<details>
<summary>Click for details</summary>

The Gradle `org.springframework.boot` plugin and the Maven `spring-boot-maven-plugin` connect Boot's application model to each build system. They do not introduce a second build engine. Instead, they add Boot-aware operations to the normal Gradle task graph or Maven lifecycle/goals.

On Gradle, applying the Boot plugin alongside Java-related plugins creates or configures tasks such as `bootRun`, `bootJar`, `bootWar`, and `bootBuildImage`. On Maven, the plugin provides goals such as `run`, `repackage`, and `build-image`. Both integration paths target the same learner-level outcomes: launch the application, package it in Boot's executable format, or create an image through supported integration.

The Gradle plugin also reacts to plugins already applied to the project. With the Java plugin present, Boot 3.3 registers BootJar, BootRun, and BootBuildImage tasks, wires the executable archive into assemble, gives the ordinary jar a plain classifier by convention, and creates development-only/runtime configurations used by Boot's packaging model. That behavior is why the plugin feels integrated with Gradle rather than like an unrelated command wrapper.

The Maven plugin integrates through Maven goals and lifecycle execution rather than Gradle task registration. When the project inherits from spring-boot-starter-parent, the parent can also preconfigure the repackage execution and useful build defaults. Learn these as two adapters around the same Boot outcomes, not as identical internal mechanisms.

### References

- [Spring Boot 3.3 — Gradle Plugin Reference](https://docs.spring.io/spring-boot/3.3/gradle-plugin/)
- [Spring Boot 3.3 — Maven Plugin Reference](https://docs.spring.io/spring-boot/3.3/maven-plugin/)

</details>

- [Back to top](#back-to-top)

---

## <a id="plugin-responsibilities">Which Run, Package, and Image Tasks Belong to the Boot Plugins?</a>

<details>
<summary>Click for details</summary>

Think of plugin responsibilities by output rather than by memorizing task names:

```text
development process
→ Gradle bootRun / Maven spring-boot:run

executable archive
→ Gradle bootJar or bootWar / Maven repackage

OCI image through Buildpacks
→ Gradle bootBuildImage / Maven build-image
```

The plugin also exposes configuration for the main class, archive layout, layers, image builder/run image, environment, publishing, and related Boot-specific behavior. These knobs configure Boot integration; they do not redefine the generic semantics of Gradle tasks or Maven phases.

Boot-specific tasks also deliberately consume normal build-tool inputs. BootRun uses the compiled source-set output and runtime classpath; BootJar packages what Gradle resolved for the runtime; repackage starts from the archive produced by Maven's normal package lifecycle. If those inputs are wrong, the Boot operation faithfully packages or launches the wrong thing.

That dependency direction is useful when debugging: first prove that the build tool produced the expected classes and dependency graph, then inspect what the Boot task did with them. A Boot plugin cannot compensate for a missing repository, an unresolved dependency, or source code that never compiled.

</details>

- [Back to top](#back-to-top)

---

## <a id="plugin-vs-build-tool">What Remains the Responsibility of Gradle or Maven?</a>

<details>
<summary>Click for details</summary>

Gradle or Maven still owns how source sets or source directories are compiled, how dependencies are resolved, how repositories are declared, how incremental work and lifecycle ordering operate, and how a multi-project build is modeled. Boot consumes those capabilities.

A useful diagnostic question is: **would this problem still exist in a non-Boot Java project?** If the answer is yes—repository authentication, a broken compiler configuration, Gradle task dependency wiring, or Maven lifecycle basics—the root cause probably belongs to the build tool. If the failure is about `BOOT-INF`, `bootJar`, `repackage`, or `bootBuildImage`, Boot integration is the better starting point.

</details>

- [Back to top](#back-to-top)

---

## <a id="plugin-choice-boundary">How Should You Reason About the Gradle and Maven Integration Paths?</a>

<details>
<summary>Click for details</summary>

Gradle and Maven expose different configuration models, so their syntax should not be forced into a fake one-to-one mapping. Learn the common Boot intent first, then the build-tool-specific mechanism.

For example, both tools can produce an executable archive, but Gradle models that as tasks such as `bootJar`, while Maven commonly attaches `repackage` to the package lifecycle. Both are valid ways to reach the same Boot packaging model.

Choose the build tool because of the project's build ecosystem, not because Boot requires one of them. Once the tool is chosen, use the official plugin path for that ecosystem and keep generic build-tool customization outside the Boot-specific mental model.

</details>

- [Back to top](#back-to-top)
