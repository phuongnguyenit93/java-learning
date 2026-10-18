<a id="back-to-top"></a>

# Spring Boot Packaging Mental Model

## Menu
- [Why Does Spring Boot Provide an Executable Packaging Model?](#packaging-purpose)
- [What Is an Executable Boot Application Artifact?](#executable-artifact)
- [How Are Application Classes and Dependencies Kept Runnable Together?](#boot-loader-layout-mental-model)
- [How Does the Packaged Application Relate to java -jar?](#run-packaged-application)
- [Where Does Packaging Detail Hand Off?](#packaging-tooling-handoff)

## <a id="packaging-purpose">Why Does Spring Boot Provide an Executable Packaging Model?</a>

<details>
<summary>Click for details</summary>

A Boot application is useful only if the code and its runtime dependencies can be delivered in a form that starts predictably outside the developer's IDE. Boot's executable packaging model gives common JVM applications a self-contained application artifact that knows how to reach both application classes and packaged dependencies.

The beginner-facing value is operational simplicity:

```text
compiled application + runtime dependencies
        ↓ packaging
one executable application archive
        ↓
java -jar application.jar
```

This does not mean Boot invented JAR files or the JVM launch protocol. Boot's build tooling and loader arrange a JAR/war layout that can preserve nested dependency jars and launch the application's real `main` method.

</details>

- [Back to top](#back-to-top)

---

## <a id="executable-artifact">What Is an Executable Boot Application Artifact?</a>

<details>
<summary>Click for details</summary>

An executable Boot artifact is a packaged application archive prepared so it can be launched directly. For the common executable JAR case, it contains application classes/resources, dependency JARs, and Boot loader classes/metadata needed to establish the runtime classpath and invoke the application entry point.

The important contrast is with a thin ordinary JAR that contains only your compiled classes and expects all dependencies to be assembled separately on the command line. Boot's executable archive can carry those dependencies inside the application artifact.

That makes deployment easier to reason about: the artifact represents the application plus the dependency set against which it was built. Detailed `bootJar`/`repackage` task behavior belongs to `build-tooling-packaging`.

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-loader-layout-mental-model">How Are Application Classes and Dependencies Kept Runnable Together?</a>

<details>
<summary>Click for details</summary>

In the standard executable JAR layout, application classes live under `BOOT-INF/classes` and dependency JARs live under `BOOT-INF/lib`. Boot's launcher understands this nested layout and creates the classpath needed to call the application's real main class.

```text
application.jar
├── BOOT-INF/classes/    ← application classes/resources
├── BOOT-INF/lib/        ← dependency jars
└── Boot launcher classes/metadata
```

This is a mental model, not a request to memorize archive internals. The evidence is that dependencies can remain packaged as nested JARs while the application is still directly runnable.

Spring Boot 3.3's `JarLauncher` expects application classes in `BOOT-INF/classes` and dependencies in `BOOT-INF/lib`. The deeper loader implementation, alternative layouts, layering, and build-task configuration belong to `build-tooling-packaging`.

### References

- [Spring Boot 3.3 — JarLauncher](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/loader/launch/JarLauncher.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="run-packaged-application">How Does the Packaged Application Relate to java -jar?</a>

<details>
<summary>Click for details</summary>

When the archive manifest points to Boot's launcher, `java -jar application.jar` starts that launcher first. The launcher establishes access to the nested application classes and dependencies, then invokes the application's configured start class, which contains the familiar `main` method.

```text
java -jar application.jar
        ↓
Boot launcher
        ↓
application + nested dependency classpath
        ↓
YourApplication.main(...)
        ↓
SpringApplication.run(...)
```

This connects packaging back to bootstrap: packaging changes how the JVM reaches the application and its dependencies; once the application's `main` method runs, the same `SpringApplication` mental model from Chapter 2 applies.

</details>

- [Back to top](#back-to-top)

---

## <a id="packaging-tooling-handoff">Where Does Packaging Detail Hand Off?</a>

<details>
<summary>Click for details</summary>

Detailed build plugins, executable archives, layered packaging, OCI images, and Buildpacks belong to the `build-tooling-packaging` module.

That owner explains `bootRun`, `bootJar`/`bootWar`, Maven repackage behavior, Boot Loader details, reproducible archives, layer metadata, `bootBuildImage`, and Cloud Native Buildpacks. Those are build and delivery mechanisms rather than prerequisites for understanding what a Boot application is.

Fundamentals only needs the stable relationship: source and dependencies become a runnable artifact; Boot's launcher can bridge the packaged layout to the application's real main class; and build tooling is responsible for creating that artifact correctly.

</details>

- [Back to top](#back-to-top)
