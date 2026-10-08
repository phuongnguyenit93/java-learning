<a id="back-to-top"></a>

# Closed-World Consequences for a Boot Application

## Menu
- [What Does the Closed-world Assumption Mean for a Boot Application?](#closed-world-model)
- [Why Does the Build-time Classpath Become Part of the Executable Model?](#fixed-build-time-classpath)
- [Why Can the Bean Graph Not Change Freely after AOT Processing?](#aot-bean-graph)
- [How Can Profiles and Properties Affect Build-time Bean Decisions?](#profile-property-build-time)
- [Which Dynamic Behaviors Deserve Native-readiness Review?](#dynamic-behavior-review)
- [How Do Closed-world Restrictions Differ from Ordinary Runtime Configuration?](#closed-world-runtime-boundary)

## <a id="closed-world-model">What Does the Closed-world Assumption Mean for a Boot Application?</a>

<details>
<summary>Click for details</summary>

For native-image generation, the compiler reasons from a **closed set of code and metadata available at build time**. Code that appears unreachable may be removed, and dynamic behavior that cannot be inferred needs explicit reachability information.

Spring AOT adapts the application to that model by turning many framework decisions into generated code and hints. The result is not “no dynamic behavior at all”; it is dynamic behavior constrained to what the build has made reachable and describable.

This mental model explains most native-only failures: something that a JVM could discover later was not visible enough when the native executable was created.

### References

- [Spring Boot 3.3 — Introducing GraalVM Native Images](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/introducing-graalvm-native-images.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="fixed-build-time-classpath">Why Does the Build-time Classpath Become Part of the Executable Model?</a>

<details>
<summary>Click for details</summary>

Classpath presence is one of Spring Boot's major decision inputs. On a normal JVM, classes can still be loaded later and conditional logic can be evaluated during startup. With AOT/native processing, the classpath used during the build helps determine auto-configuration, bean definitions, generated proxies, and reachability.

Adding a JAR only after the native executable has been built does not make its classes magically part of that executable. Conversely, removing or replacing a build-time dependency can change the generated application model even if source code did not change.

Treat dependency versions and build-time classpath composition as native build inputs that need the same reproducibility discipline as source code.

</details>

- [Back to top](#back-to-top)

---

## <a id="aot-bean-graph">Why Can the Bean Graph Not Change Freely after AOT Processing?</a>

<details>
<summary>Click for details</summary>

Spring AOT prepares a specific `BeanFactory` model and generates initialization for that model. Because those decisions are encoded ahead of runtime, a native application cannot depend on arbitrary late bean registration patterns that were absent from the analyzed model.

This is especially important for libraries that register infrastructure dynamically through custom bootstrap code, runtime scanning, or singleton registration. Such libraries need an AOT-compatible integration path so Spring can understand the resulting bean graph during processing.

Application configuration can still supply runtime values; the restriction concerns **structural changes to the prepared application model**, not every property read performed after startup.

</details>

- [Back to top](#back-to-top)

---

## <a id="profile-property-build-time">How Can Profiles and Properties Affect Build-time Bean Decisions?</a>

<details>
<summary>Click for details</summary>

Profiles and properties are ordinary configuration concepts, but AOT changes the timing when they control whether bean definitions exist. During AOT processing, Spring fully prepares the `BeanFactory` and evaluates conditions against the **build-time environment**. If a profile participates in structural configuration, that profile must be supplied to the AOT/build environment; the resulting bean structure is then encoded in the generated application model. `@Profile` and profile-specific configuration therefore have limitations in an AOT-processed application: changing the active profile only at runtime cannot rebuild a different bean graph.

The same boundary applies to properties that decide whether a bean is created. A condition such as `@ConditionalOnProperty` or a conventional `*.enabled` switch cannot be treated as a runtime structural toggle after AOT has prepared the application. If such a condition is part of the native application design, the value that selects the bean structure has to be present in the environment used for AOT processing.

Distinguish that structural decision from ordinary runtime values:

```text
property changes a value used by an already-known bean
→ often remains ordinary runtime configuration

profile/property changes whether a bean/configuration exists
→ condition is resolved while AOT prepares the structural model
→ runtime changes cannot switch to a different generated bean graph
```

So a database URL, timeout, credential, or another value consumed by an already-known bean can still vary at runtime when it does not change bean creation. What cannot vary freely is the **structure** that AOT has already generated.

### References

- [Spring Boot 3.3 — Ahead-of-Time Processing](https://docs.spring.io/spring-boot/3.3/maven-plugin/aot.html)
- [Spring Boot 3.3 — Ahead-of-Time Processing With the JVM](https://docs.spring.io/spring-boot/3.3/reference/packaging/aot.html)
- [Spring Boot 3.3 — Ahead-of-Time Processing: Conditions](https://docs.spring.io/spring-boot/3.3/how-to/aot.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="dynamic-behavior-review">Which Dynamic Behaviors Deserve Native-readiness Review?</a>

<details>
<summary>Click for details</summary>

Review behavior whose target is chosen dynamically and may not appear as an ordinary direct code reference:

```text
reflection over application/library types
classpath resource lookup
JDK dynamic proxies
serialization/deserialization requiring reflective construction
dynamic class loading or generated bytecode
JNI/native libraries
configuration that names classes/resources indirectly
```

This is a **review checklist**, not a statement that every use is unsupported. Spring and many libraries contribute hints automatically. The question is whether the native build has enough evidence to preserve the required target and operation.

</details>

- [Back to top](#back-to-top)

---

## <a id="closed-world-runtime-boundary">How Do Closed-world Restrictions Differ from Ordinary Runtime Configuration?</a>

<details>
<summary>Click for details</summary>

An application still reads configuration, accepts requests, opens database connections, schedules tasks, and changes business state after native startup. Closed-world does not mean “everything is frozen.” It means the executable's reachable code and prepared framework structure are largely fixed by build-time analysis.

Use this distinction during diagnosis. A wrong database URL is ordinary runtime configuration. A runtime property that tries to activate a bean definition excluded by the AOT model is a structural AOT mismatch. A reflective call failing only natively may be missing reachability metadata.

</details>

- [Back to top](#back-to-top)
