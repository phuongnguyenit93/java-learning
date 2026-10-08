<a id="back-to-top"></a>

# Running Applications with Build Tooling

## Menu
- [What Is a Boot-Aware Development Run?](#boot-aware-development-run)
- [How Does Gradle `bootRun` Launch the Application?](#gradle-bootrun)
- [How Does Maven `spring-boot:run` Launch the Application?](#maven-spring-boot-run)
- [When Should You Use a Build-Tool Run Instead of `java -jar`?](#build-tool-run-vs-packaged-run)
- [What Does the Run Task Configure, and What Still Belongs to `SpringApplication`?](#run-task-runtime-boundary)

## <a id="boot-aware-development-run">What Is a Boot-Aware Development Run?</a>

<details>
<summary>Click for details</summary>

A Boot-aware development run launches the application directly from the build's compiled output and runtime classpath. It is optimized for the edit-build-run loop: you do not need to produce the final executable archive before every local start.

This is different from validating a packaged artifact. The development run proves that the application can start with the build's current classpath and configuration. A later `java -jar` check proves that the **packaged layout and launcher** also work as intended.

A practical consequence is that development run success is evidence about source-set output and runtime dependency resolution, not about the final archive manifest or nested-library layout. Keep that evidence narrow: it proves the application can launch from the build model, then packaging adds another boundary that must be verified separately.

</details>

- [Back to top](#back-to-top)

---

## <a id="gradle-bootrun">How Does Gradle `bootRun` Launch the Application?</a>

<details>
<summary>Click for details</summary>

The Boot Gradle plugin provides `bootRun`, a Java-execution task configured around the application's main source set. It uses the development runtime classpath, resolves the application's main class, and lets the build configure JVM arguments, application arguments, environment, system properties, and related launch inputs.

Typical use:

```bash
./gradlew bootRun
```

The task is convenient because changes flow through Gradle's normal build model. It is not an executable-archive test: `bootRun` does not require the application to start through the packaged Boot loader layout.

In Boot 3.3, BootRun is a JavaExec subclass. That means familiar JavaExec launch controls remain available while Boot supplies useful conventions such as the main runtime classpath and automatic main-class discovery. Boot also enables an optimized development launch by default; it can be disabled when a troubleshooting case requires the normal JVM launch behavior.

Application arguments and JVM/system properties are separate inputs. For example, --args passes application arguments, while JavaExec configuration controls JVM arguments and system properties. Mixing those channels is a common reason a property appears to be ignored.

### References

- [Spring Boot 3.3 Gradle Plugin — Running your Application](https://docs.spring.io/spring-boot/3.3/gradle-plugin/running.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="maven-spring-boot-run">How Does Maven `spring-boot:run` Launch the Application?</a>

<details>
<summary>Click for details</summary>

The Maven plugin's `spring-boot:run` goal provides the corresponding development launch path for Maven builds. The plugin assembles the project's application classpath and launches the configured Boot main class with plugin options for arguments and environment-related inputs.

Typical use:

```bash
./mvnw spring-boot:run
```

As with Gradle, this path is about fast development execution. Maven still owns dependency resolution and its build lifecycle; once the process is started, `SpringApplication` owns the Boot runtime lifecycle.

The Maven run goal likewise has dedicated channels for application arguments, JVM arguments, environment variables, system properties, and active profiles. Prefer those plugin inputs over embedding local machine assumptions into the POM. They make the development launch reproducible and easier to reproduce on another workstation.

If the application must first package correctly before a problem can appear, the run goal is the wrong evidence source. Move to package plus java -jar or to the image path so the failing packaging boundary is actually exercised.

### References

- [Spring Boot 3.3 Maven Plugin — Running your Application](https://docs.spring.io/spring-boot/3.3/maven-plugin/run.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="build-tool-run-vs-packaged-run">When Should You Use a Build-Tool Run Instead of `java -jar`?</a>

<details>
<summary>Click for details</summary>

Use `bootRun` or `spring-boot:run` when the main goal is rapid development feedback. Use `java -jar` when you need evidence about the artifact that will actually be handed off.

```text
editing/debugging locally
→ build-tool run

checking manifest/main class/BOOT-INF/loader/dependency packaging
→ build executable archive
→ java -jar artifact
```

A project can pass development-run tests and still produce a broken archive because packaging configuration is wrong. That is why a delivery pipeline should eventually validate the packaged form, not assume that a successful development launch proves packaging correctness.

The two launch paths can also use different dependency sets. Development-only dependencies may participate in BootRun while being intentionally excluded from the production archive. That is useful for devtools, but it means a local run can succeed because a development-only component is present even though the packaged runtime will not contain it.

A reliable release check therefore treats the packaged artifact as a separate test subject. Inspect its contents when necessary, launch the exact JAR/image intended for delivery, and avoid substituting a successful IDE or build-tool run as proof of release packaging.

</details>

- [Back to top](#back-to-top)

---

## <a id="run-task-runtime-boundary">What Does the Run Task Configure, and What Still Belongs to `SpringApplication`?</a>

<details>
<summary>Click for details</summary>

The build plugin controls **how the JVM process is launched**: classpath, selected main class, JVM/application arguments, environment, and build-tool-specific launch configuration. Once the application's `main` method calls `SpringApplication.run`, the runtime model takes over.

Events, context creation, runners, application availability, failure analysis, task execution, and shutdown are therefore not `bootRun` semantics. If the same runtime problem also occurs with `java -jar`, investigate application-runtime or another runtime owner rather than continuing to tune the build task.

</details>

- [Back to top](#back-to-top)
