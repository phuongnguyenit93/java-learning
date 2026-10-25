<a id="back-to-top"></a>

# Testing Auto-configuration Decisions

## Menu
- [Why Auto-configuration Needs Focused Context Tests](#why-focused-context-tests)
- [ApplicationContextRunner](#application-context-runner)
- [Back-off and Property Variant Tests](#back-off-and-property-tests)
- [Testing Classpath Absence with FilteredClassLoader](#filtered-class-loader)
- [Servlet and Reactive Context Runners](#web-context-runners)
- [Condition Evaluation Evidence in Tests](#condition-report-in-tests)

## <a id="why-focused-context-tests">Why Auto-configuration Needs Focused Context Tests</a>

<details>
<summary>Click for details</summary>

Auto-configuration behavior is a decision matrix, not just one happy-path startup. The result can change with user beans, properties, classpath contents, resources, and application type.

A full SpringBootTest can prove that one whole application starts, but it is often too broad to explain why one condition matched. Focused context tests let the test construct exactly the state relevant to the auto-configuration.

Useful test dimensions include:

- default state;
- user-provided replacement bean;
- property enabled/disabled variants;
- optional library present/absent;
- servlet, reactive, and non-web contexts;
- diagnostics for an expected non-match.

The goal is executable evidence for the auto-configuration contract.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-context-runner">ApplicationContextRunner</a>

<details>
<summary>Click for details</summary>

ApplicationContextRunner creates a small configurable ApplicationContext for each test invocation. AutoConfigurations.of can register the auto-configuration under test without bootstrapping a complete production application.

~~~java
private final ApplicationContextRunner contextRunner =
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(AcmeClientAutoConfiguration.class));
~~~

Each run starts a fresh context, allows assertions against that context, and closes it afterward. Additional configuration, properties, and classloaders can be layered onto the runner.

This style makes condition evidence local: the test setup describes the input state and the assertions describe exactly which beans should or should not exist.

</details>

- [Back to top](#back-to-top)

---

## <a id="back-off-and-property-tests">Back-off and Property Variant Tests</a>

<details>
<summary>Click for details</summary>

Back-off and properties should be tested as deliberate variants rather than assumed from the annotations.

~~~java
contextRunner
    .withUserConfiguration(CustomClientConfiguration.class)
    .run(context ->
        assertThat(context).hasSingleBean(AcmeClient.class));
~~~

The test should distinguish the custom bean from the default and verify that only the intended one remains.

Property variants can be expressed with runner configuration values:

~~~text
acme.client.enabled=true
→ default integration present

acme.client.enabled=false
→ integration absent
~~~

Testing both sides catches inverted havingValue logic, incorrect matchIfMissing assumptions, and back-off conditions that target the wrong type.

</details>

- [Back to top](#back-to-top)

---

## <a id="filtered-class-loader">Testing Classpath Absence with FilteredClassLoader</a>

<details>
<summary>Click for details</summary>

FilteredClassLoader lets a test simulate a classpath where a library or package is absent even though the test build itself contains that dependency.

~~~java
contextRunner
    .withClassLoader(new FilteredClassLoader(AcmeClient.class))
    .run(context ->
        assertThat(context).doesNotHaveBean(AcmeClient.class));
~~~

This is strong evidence for optional-dependency design. A correct configuration should simply stop applying when the required class is absent. It should not fail with NoClassDefFoundError or another linkage problem.

Use the test to validate both the condition and the isolation structure around optional method signatures.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-context-runners">Servlet and Reactive Context Runners</a>

<details>
<summary>Click for details</summary>

ApplicationContextRunner creates a non-web context. When the configuration specifically depends on a servlet or reactive environment, use the corresponding WebApplicationContextRunner or ReactiveWebApplicationContextRunner.

~~~text
core auto-configuration
→ ApplicationContextRunner

servlet-only auto-configuration
→ WebApplicationContextRunner

reactive-only auto-configuration
→ ReactiveWebApplicationContextRunner
~~~

Choosing the correct runner is part of the test input. A web condition should not be “proven” in the wrong context type.

This testing milestone stays focused on auto-configuration decisions. Broader web testing and Boot test-slice strategy belong to their dedicated modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="condition-report-in-tests">Condition Evaluation Evidence in Tests</a>

<details>
<summary>Click for details</summary>

Sometimes the best assertion is not only whether a bean exists but why a condition selected that outcome. A test can attach Boot's condition-report logging listener or inspect condition evidence while running the focused context.

This is especially useful for a failing authoring test:

~~~text
expected bean absent
        ↓
log Condition Evaluation Report
        ↓
identify negative condition
        ↓
fix the actual applicability rule or test input
~~~

Avoid turning every test into a snapshot of the entire report. Bean presence/absence and behavior remain the primary contract; report output is supporting diagnostic evidence.

With the auto-configuration behavior now verified, the final chapter packages the model into a starter: dependency opinion, naming, configuration namespace, and the end-to-end path from adding a dependency to receiving conditional defaults.

</details>

- [Back to top](#back-to-top)
