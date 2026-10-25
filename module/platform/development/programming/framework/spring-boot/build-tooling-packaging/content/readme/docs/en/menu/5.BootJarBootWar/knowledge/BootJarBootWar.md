<a id="back-to-top"></a>

# `bootJar`, `bootWar`, and Repackaging

## Menu
- [How Do `bootJar`, `bootWar`, and Maven `repackage` Produce Boot Archives?](#bootjar-bootwar-repackage)
- [How Is a Boot Executable Archive Different from a Plain Library Artifact?](#executable-vs-plain-archive)
- [When Should You Package an Executable JAR or WAR?](#jar-vs-war-packaging)
- [How Does the Packaged Archive Identify the Application Entry Point?](#packaging-main-class)

## <a id="bootjar-bootwar-repackage">How Do `bootJar`, `bootWar`, and Maven `repackage` Produce Boot Archives?</a>

<details>
<summary>Click for details</summary>

Gradle's Boot plugin provides `bootJar` for executable JARs and, when the War plugin is used, `bootWar` for executable WARs. Maven reaches the same Boot archive model through the `repackage` goal, which takes the archive produced by the normal package lifecycle and rewrites/augments it into Boot's executable layout.

```text
application classes + runtime dependencies
        ↓
bootJar / bootWar / repackage
        ↓
Boot executable archive
        ↓
Spring Boot Loader launches nested application content
```

The normal build tool still compiles the project and creates its inputs. Boot's packaging step is responsible for the executable layout and launcher metadata.

With Gradle, BootJar and BootWar are specialized Jar and War tasks, so normal archive configuration still applies alongside Boot-specific features. With Maven, repackage transforms the archive produced in the package lifecycle; by default the original non-executable artifact is retained as an .original file unless a classifier strategy is used.

This distinction matters when a repository publishes more than one artifact. Be explicit about which file is the executable application and which file, if any, remains a plain artifact for another consumer. Artifact coordinates alone may not tell the whole story when classifiers are involved.

### References

- [Spring Boot 3.3 Gradle Plugin — Packaging Executable Archives](https://docs.spring.io/spring-boot/3.3/gradle-plugin/packaging.html)
- [Spring Boot 3.3 Maven Plugin — Packaging Executable Archives](https://docs.spring.io/spring-boot/3.3/maven-plugin/packaging.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="executable-vs-plain-archive">How Is a Boot Executable Archive Different from a Plain Library Artifact?</a>

<details>
<summary>Click for details</summary>

A plain library JAR is normally intended to be placed **on another application's classpath**. A Boot executable archive is intended to be a self-contained application delivery unit. It includes application classes, runtime dependency JARs in Boot's nested layout, and launcher metadata/code needed for `java -jar` execution.

This distinction matters in multi-purpose projects. Publishing a library and shipping an application are different outcomes. With Gradle, the standard `jar` and Boot's `bootJar` are separate archive tasks; project configuration should make it clear which artifact is the application delivery output and which, if any, is a reusable library artifact.

Gradle makes this separation visible by convention: when bootJar or bootWar is configured, the ordinary jar or war receives the plain classifier so both outputs can coexist. The plain task can be disabled when the project truly does not need it, but that decision must consider other tooling; for example, native-image workflows can depend on the ordinary jar.

Do not publish both artifacts under ambiguous names. A library consumer should not accidentally depend on the executable Boot archive, whose nested dependency layout is intended for launching rather than for use as a normal classpath library.

</details>

- [Back to top](#back-to-top)

---

## <a id="jar-vs-war-packaging">When Should You Package an Executable JAR or WAR?</a>

<details>
<summary>Click for details</summary>

An executable JAR is the normal choice when the application owns its Boot-managed runtime and starts with `java -jar`. The embedded server, when present, is part of the application's dependency model.

A WAR is useful when the delivery environment requires traditional servlet-container deployment. Boot can also make a WAR executable, but a deployable WAR must model container-provided dependencies correctly so that the same server libraries are not packaged as ordinary application libraries.

```text
self-contained Boot process
→ prefer executable JAR

external servlet-container deployment requirement
→ consider WAR
```

The choice is a deployment contract, not a statement that WAR is “more enterprise” or JAR is “only for development.”

For an executable WAR that must also deploy to an external servlet container, Boot keeps container-provided libraries under WEB-INF/lib-provided rather than mixing them with ordinary WEB-INF/lib content. In Gradle, providedRuntime is the preferred model for those dependencies because it also keeps them available to the test runtime.

That layout is evidence of the dual contract: the archive can launch itself, yet it can also avoid conflicting with server libraries when the external container owns them. If the organization has no external-container requirement, the executable JAR path is usually simpler.

</details>

- [Back to top](#back-to-top)

---

## <a id="packaging-main-class">How Does the Packaged Archive Identify the Application Entry Point?</a>

<details>
<summary>Click for details</summary>

An executable Boot archive separates the **launcher entry point** from the application's own main class. Manifest metadata tells `java -jar` to enter through Spring Boot Loader and records the application class that the launcher should eventually invoke.

Build plugins can usually discover a single suitable main class. If the project contains multiple candidates or discovery is ambiguous, configure the main class explicitly rather than relying on accidental ordering.

This explains a common diagnostic split: a missing or ambiguous main-class error during packaging belongs to build configuration, while an exception after the application's `main` method begins belongs to runtime startup.

The manifest normally uses a Boot loader class as Main-Class and stores the application's entry point as Start-Class. Maven's repackage goal manages those entries, so when automatic detection is insufficient the application main class should be configured through the Boot plugin rather than by manually fighting the jar plugin's manifest.

This is also why changing Main-Class directly to the application class can break the nested-archive launch model: it bypasses the loader that knows how to construct the classpath from BOOT-INF or WEB-INF.

</details>

- [Back to top](#back-to-top)
