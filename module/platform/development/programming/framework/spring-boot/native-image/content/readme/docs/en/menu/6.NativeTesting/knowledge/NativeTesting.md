<a id="back-to-top"></a>

# Test AOT and Native Behavior Deliberately

## Menu
- [Which Native-specific Risks Need Dedicated Tests?](#native-testing-purpose)
- [Why Should Most Test Feedback Stay on the JVM?](#jvm-first-test-strategy)
- [When Is Running AOT-processed Code on the JVM Useful?](#aot-jvm-validation)
- [How Do Maven `process-test-aot` and Gradle `processTestAot` Prepare Test Contexts?](#process-test-aot)
- [How Do Maven's `nativeTest` Profile and Gradle's `nativeTest` Task Run Native Tests?](#native-test-execution)
- [How Should Native-test Cost Influence Local and CI Strategy?](#native-test-cost)
- [Where Does Native-specific Testing Hand Off to the General Boot Testing Module?](#native-testing-boundary)

## <a id="native-testing-purpose">Which Native-specific Risks Need Dedicated Tests?</a>

<details>
<summary>Click for details</summary>

Native-specific tests are most valuable for behavior that can diverge because of AOT or closed-world constraints: reflective binding, resources, serialization, dynamic proxies, custom runtime hints, third-party native support, and configuration that changes the prepared bean model.

Ordinary business rules do not become more correct by compiling every unit test into native code. Keep native testing focused on **native-sensitive integration boundaries** and on representative application startup/use cases.

### References

- [Spring Boot 3.3 — Testing GraalVM Native Images](https://docs.spring.io/spring-boot/3.3/how-to/native-image/testing-native-applications.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="jvm-first-test-strategy">Why Should Most Test Feedback Stay on the JVM?</a>

<details>
<summary>Click for details</summary>

JVM tests are dramatically cheaper to compile and run, have mature debugging/tooling, and already validate most application logic. Native compilation is intentionally expensive because it performs whole-program-style reachability analysis and native code generation.

A cost-aware test pyramid therefore keeps frequent feedback on the JVM and adds native checks where they prove something the JVM cannot:

```text
unit + ordinary Boot integration tests
→ fast JVM feedback

AOT-focused validation
→ prepared application model

selected native tests / smoke tests
→ actual closed-world executable behavior
```

This strategy catches native risks without turning every edit into a long native build.

</details>

- [Back to top](#back-to-top)

---

## <a id="aot-jvm-validation">When Is Running AOT-processed Code on the JVM Useful?</a>

<details>
<summary>Click for details</summary>

Spring can run an AOT-processed application on the JVM with AOT mode enabled. That does not reproduce GraalVM's closed-world compiler, but it validates an important intermediate boundary: whether the generated initialization/assets represent the application correctly.

When the JAR already contains AOT-generated code, the check is explicit:

```text
java -Dspring.aot.enabled=true -jar myapplication.jar
```

For Maven projects that inherit from `spring-boot-starter-parent`, producing such a JAR means building with the parent-provided `native` profile active. A non-parent Maven build must configure the equivalent `process-aot` execution explicitly. For Gradle, the JAR must be built with Boot's `org.springframework.boot.aot` plugin enabled. Applying GraalVM Native Build Tools alongside Spring Boot automatically applies/configures that AOT support for the native workflow, but GraalVM compilation is not required merely to exercise the generated AOT initialization on the JVM.

Use it when normal JVM startup succeeds but you need to decide whether a failure belongs to Spring's AOT preparation or the later native compilation/runtime layer. If AOT-mode JVM execution already fails, a full native build is unlikely to provide faster diagnosis.

</details>

- [Back to top](#back-to-top)

---

## <a id="process-test-aot">How Do Maven `process-test-aot` and Gradle `processTestAot` Prepare Test Contexts?</a>

<details>
<summary>Click for details</summary>

Spring's TestContext Framework can participate in AOT processing. Boot's Maven plugin exposes `process-test-aot`; the Gradle integration exposes the corresponding `processTestAot` path through native tooling. Eligible Spring test contexts are analyzed and initialization code is generated for use in native test execution.

This processing is separate from ordinary application AOT because tests can define additional contexts, configurations, and infrastructure. A test context that relies on unsupported runtime dynamism may therefore need native-specific adjustment even when the main application context is ready.

</details>

- [Back to top](#back-to-top)

---

## <a id="native-test-execution">How Do Maven's `nativeTest` Profile and Gradle's `nativeTest` Task Run Native Tests?</a>

<details>
<summary>Click for details</summary>

With the supported native test setup, the build AOT-processes eligible tests, compiles a native test executable, and runs those tests in native form. Maven projects inheriting from `spring-boot-starter-parent` get the `nativeTest` profile; Gradle projects with GraalVM Native Build Tools get the `nativeTest` task.

```text
mvn -PnativeTest test
gradle nativeTest
```

Without `spring-boot-starter-parent`, Maven does not gain `nativeTest` automatically; configure the equivalent Boot `process-test-aot` and GraalVM Native Build Tools test execution explicitly.

The goal is not simply “run JUnit under a different VM.” Native test execution validates that the test application contexts, hints, resources, and code paths survive native-image analysis and execute in the resulting native binary.

When a native test fails, compare it with the same JVM test first. The difference is often the quickest clue that reachability/AOT—not business logic—is responsible.

</details>

- [Back to top](#back-to-top)

---

## <a id="native-test-cost">How Should Native-test Cost Influence Local and CI Strategy?</a>

<details>
<summary>Click for details</summary>

Native tests consume more build time and machine resources, so place them where their signal justifies the cost. Developers can run focused native tests while changing hints or native-sensitive integrations; CI can run a representative native suite on pull requests, scheduled builds, or release candidates according to project risk.

Cache/toolchain strategy also matters, but avoid hiding correctness behind cache assumptions. A fresh CI environment should still be able to reproduce the native executable from declared inputs.

Measure native build duration and failure frequency. If the suite is too expensive, first reduce duplication and focus on native-sensitive paths rather than dropping native verification entirely.

</details>

- [Back to top](#back-to-top)

---

## <a id="native-testing-boundary">Where Does Native-specific Testing Hand Off to the General Boot Testing Module?</a>

<details>
<summary>Click for details</summary>

The `testing` module owns `@SpringBootTest`, slices, web environments, test auto-configuration, property overrides, dependency replacement, Testcontainers service connections, and general integration-test strategy.

Native-image consumes those existing tests and asks one additional question: **does the relevant behavior still work after AOT processing and native compilation?** Only that AOT/native-specific dimension belongs here.

</details>

- [Back to top](#back-to-top)
