<a id="back-to-top"></a>

# Executable Archives and Spring Boot Loader

## Menu
- [Why Does Spring Boot Use Nested Archives?](#nested-archive-model)
- [How Are `BOOT-INF` and `WEB-INF` Laid Out?](#boot-archive-layout)
- [Why Is Spring Boot Loader Needed?](#spring-boot-loader-purpose)
- [What Do Classpath and Layer Indexes Describe?](#archive-indexes)
- [When Does Extracting an Executable Archive Matter?](#archive-extraction-boundary)

## <a id="nested-archive-model">Why Does Spring Boot Use Nested Archives?</a>

<details>
<summary>Click for details</summary>

An application often depends on dozens of existing JARs. Flattening all of them into one giant archive can lose artifact boundaries and can create collisions around resources or metadata. Boot instead keeps dependency JARs nested inside the executable archive.

```text
application classes
dependency-a.jar
dependency-b.jar
dependency-c.jar
        ↓
one executable Boot archive
while dependency JARs remain distinct nested artifacts
```

The archive is therefore easy to distribute as one file while retaining a recognizable dependency structure. Spring Boot Loader is what makes that nested structure executable.

### References

- [Spring Boot 3.3 — Executable Jar Format](https://docs.spring.io/spring-boot/3.3/specification/executable-jar/)

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-archive-layout">How Are `BOOT-INF` and `WEB-INF` Laid Out?</a>

<details>
<summary>Click for details</summary>

An executable JAR places application classes/resources under `BOOT-INF/classes` and dependency JARs under `BOOT-INF/lib`. This keeps application-owned content separate from its runtime dependencies.

Executable WARs use the servlet-oriented `WEB-INF` structure: application classes and ordinary libraries live in the corresponding `WEB-INF` locations, while dependencies intended to be provided by an external container are kept separate from the normal packaged runtime libraries.

You do not normally manipulate these directories by hand. They are useful diagnostic evidence: inspecting the archive can reveal whether an expected class or dependency was packaged in the wrong place or not packaged at all.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-boot-loader-purpose">Why Is Spring Boot Loader Needed?</a>

<details>
<summary>Click for details</summary>

The JVM's ordinary `java -jar` model does not by itself treat arbitrary nested dependency JARs as the application's normal classpath. Spring Boot Loader provides the launch support that understands Boot's executable archive structure, constructs the effective classpath from the packaged locations, and invokes the application's configured main class.

This loader is a packaging/runtime-launch mechanism, not the Spring container. It runs **before** `SpringApplication` creates an `ApplicationContext`. If the loader cannot locate packaged classes, the problem is still in the delivery artifact; bean creation and auto-configuration have not started yet.

Loader behavior is part of the executable format contract. If startup fails before the Start-Class is invoked, inspect the manifest, archive layout, nested libraries, and loader configuration before investigating Spring beans. The boundary gives a precise troubleshooting checkpoint between "the artifact cannot launch" and "the Boot application launched but failed".

Some libraries cannot operate correctly while remaining nested. Boot packaging provides a requires-unpack mechanism for such exceptional dependencies so the loader can expand them to a temporary location at runtime. Use this only for libraries that truly require filesystem access; it is not a general performance optimization.

</details>

- [Back to top](#back-to-top)

---

## <a id="archive-indexes">What Do Classpath and Layer Indexes Describe?</a>

<details>
<summary>Click for details</summary>

Boot archives can contain index metadata that describes packaged content without changing the source-code dependency model. A classpath index can provide an explicit ordering of nested classpath entries. A layers index describes which archive contents belong to which logical packaging layer.

These indexes answer different questions:

```text
classpath index
→ in what packaged classpath order should entries be considered?

layers index
→ which packaged files belong to which extraction/image layer?
```

They are packaging metadata. They do not replace Gradle/Maven dependency declarations and do not define container scheduling or runtime networking.

The classpath index is optional launch metadata, while layers.idx is packaging metadata used to describe layer membership. Neither file changes Gradle/Maven's dependency graph; they describe the already-packaged artifact. If an index looks wrong, trace back to packaging inputs rather than trying to fix dependency resolution by editing the index manually.

Treat generated indexes as build outputs. Hand-editing them after packaging makes the archive harder to reproduce and creates a delivery artifact that no longer corresponds to source-controlled build configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="archive-extraction-boundary">When Does Extracting an Executable Archive Matter?</a>

<details>
<summary>Click for details</summary>

Running directly with `java -jar` is the simplest delivery model, but some production environments prefer an extracted form. Spring Boot 3.3 supports an extraction workflow through its tools jar mode so that dependencies can live outside the application JAR and the resulting layout can be started normally.

For example, the supported tooling can extract an executable archive before deployment. This can reduce the nested-archive startup overhead and can fit platforms that cache or manage application files separately.

Extraction changes the **delivery layout**, not application semantics. After the process starts, the same Boot application runtime model applies.

Boot 3.3's tools jar mode can extract an executable application with java -Djarmode=tools -jar app.jar extract. The efficient default layout places libraries in a separate lib directory and leaves an application jar whose manifest references those libraries. Production then launches the extracted application jar normally.

Extraction can reduce the small startup cost of reading classes from nested jars and can improve fit with platforms that cache application files separately. After the application has started, extraction does not create a different Spring runtime model; it is a delivery-layout choice.

### References

- [Spring Boot 3.3 — Efficient Deployments](https://docs.spring.io/spring-boot/3.3/reference/packaging/efficient.html)

</details>

- [Back to top](#back-to-top)
