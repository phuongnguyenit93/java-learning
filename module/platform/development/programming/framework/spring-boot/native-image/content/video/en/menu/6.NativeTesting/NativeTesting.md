---
video:
  url: ""
---

# Test AOT and Native Behavior Deliberately

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## Which Native-specific Risks Need Dedicated Tests?

<!-- VIDEO_SECTION -->

### Scene 1 — Test what can diverge after AOT and closed-world analysis

**Time:** `00:00–00:43`

**Visual:**

Build a three-layer test pyramid. On the native-sensitive top layer, pin cards for reflective binding, resources, proxies, serialization, custom hints, third-party native support, and structural configuration. Keep business-rule unit tests in the wide JVM base.

**Script:**

Native testing is most valuable where the execution model can diverge from the JVM. Reflective binding, resource inclusion, dynamic proxies, serialization, custom Runtime Hints, third-party native integration, and configuration that affects the prepared bean model all deserve focused native evidence. Ordinary business rules do not become more correct just because every unit test is compiled into a native binary. The goal is to test the boundaries AOT and closed-world execution can actually change.

**Purpose:**

Define the scope of native-specific testing before discussing cost or tooling.

## Why Should Most Test Feedback Stay on the JVM?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:43–00:54`

**Visual:**

Add a cost arrow rising from JVM tests to native tests and a feedback-speed arrow falling in the opposite direction.

**Script:**

The native layer gives unique confidence, but it is also the most expensive feedback loop. That is why it should sit on top of, not replace, the JVM test base.

**Purpose:**

Connect native-specific risk to a cost-aware testing pyramid.

### Scene 1 — Keep the fast loop where it is strongest

**Time:** `00:54–01:32`

**Visual:**

Show three clocks: unit/JVM integration tests in seconds, AOT-focused checks in the middle, native compile/test as the slowest. Add debugger/tooling icons beside the JVM layer.

**Script:**

JVM tests compile and run much faster and have mature debugging and tooling. They already validate most application logic and Spring integration. Native compilation performs reachability analysis and native code generation, so repeating it for every edit would make the feedback loop unnecessarily expensive. A practical strategy keeps unit and ordinary Boot integration tests on the JVM, adds AOT-focused checks when generated structure matters, and reserves native execution for paths where only the native artifact can prove the behavior.

**Purpose:**

Explain why “more native tests” is not automatically a better testing strategy.

## When Is Running AOT-processed Code on the JVM Useful?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:32–01:43`

**Visual:**

Insert an intermediate rung between JVM and native labeled `AOT-generated code on JVM`.

**Script:**

There is a useful middle boundary: run the generated Spring initialization without paying for GraalVM compilation yet.

**Purpose:**

Introduce AOT-on-JVM as a diagnostic layer between ordinary tests and native execution.

### Scene 1 — Validate generated initialization before native compilation

**Time:** `01:43–02:22`

**Visual:**

Show a terminal running `java -Dspring.aot.enabled=true -jar myapplication.jar`. Next to it, highlight that the JAR must already contain generated AOT code from Boot's AOT plugin; show GraalVM Native Build Tools as one native-workflow route that applies/configures that support automatically. Put a boundary note: `does not reproduce GraalVM closed-world analysis`.

**Script:**

When the JAR contains AOT-generated code, Spring can start it on the JVM with `spring.aot.enabled=true`. In Gradle, Boot's `org.springframework.boot.aot` plugin is the direct AOT-on-JVM requirement; applying GraalVM Native Build Tools alongside Spring Boot automatically enables/configures that AOT support for the native workflow. This check exercises the generated initialization and prepared application model without reproducing GraalVM's closed-world compiler. If normal JVM startup works but AOT-mode JVM startup fails, you have strong evidence that the problem belongs to Spring's AOT preparation rather than a later native compiler or runtime layer. It is a cheaper diagnostic checkpoint before a full native build.

**Purpose:**

Show exactly what AOT-on-JVM can prove and what it cannot.

## How Do Maven `process-test-aot` and Gradle `processTestAot` Prepare Test Contexts?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:22–02:33`

**Visual:**

Move from the application context to several test `ApplicationContext` boxes discovered from the test suite.

**Script:**

Application AOT is only part of native testing. Spring tests can create their own contexts, so those contexts need their own AOT preparation.

**Purpose:**

Explain why test AOT is a distinct processing step rather than a reuse of only the main application output.

### Scene 1 — Discover test contexts, AOT-process each one, generate test assets

**Time:** `02:33–03:17`

**Visual:**

Animate `test discovery` → `required ApplicationContext instances` → `AOT processing per context` → `generated test initializers/assets`. Label the Maven entry `process-test-aot` and the Gradle task `processTestAot` on the same flow.

**Script:**

Spring's TestContext Framework first identifies the application contexts that eligible tests require. AOT processing is then applied to those contexts, generating initialization assets that native test execution can use. Boot exposes that preparation as Maven `process-test-aot` and Gradle `processTestAot` in the supported native setup. This is separate from main-application AOT because tests can add configuration, infrastructure, and context shapes that the production application never creates.

**Purpose:**

Explain the TestContext discovery and generated-initializer flow that makes test AOT distinct from application AOT.

## How Do Maven's `nativeTest` Profile and Gradle's `nativeTest` Task Run Native Tests?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:17–03:28`

**Visual:**

Take the generated test initializers and feed them into a native test executable containing a JUnit engine.

**Script:**

Prepared test contexts are still only build evidence. `nativeTest` turns that evidence into an executable and runs the tests inside the native world we actually need to validate.

**Purpose:**

Move from test AOT preparation to real native test execution.

### Scene 1 — Compile and execute the native test image

**Time:** `03:28–04:10`

**Visual:**

Show `mvn -PnativeTest test` and `gradle nativeTest`. Behind both commands, animate: generated test assets + tests + JUnit TestEngine → native test executable → test report.

**Script:**

With the supported setup, Maven's parent-provided `nativeTest` profile and Gradle's `nativeTest` task AOT-process eligible tests, compile a native test executable, and run those tests in native form. The result is not simply JUnit under a different VM: the test contexts, hints, resources, and code paths have survived native-image analysis and execute from the resulting native binary. If Maven does not inherit `spring-boot-starter-parent`, the equivalent Boot test-AOT and Native Build Tools executions must be configured explicitly.

**Purpose:**

Explain what nativeTest proves beyond ordinary JVM execution and where Maven parent convenience applies.

## How Should Native-test Cost Influence Local and CI Strategy?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:10–04:21`

**Visual:**

Convert the test pipeline into a CI matrix with columns `every edit`, `pull request`, `scheduled/release`.

**Script:**

Because native tests prove something unique and cost more, their placement should be driven by risk and signal rather than habit.

**Purpose:**

Turn native test mechanics into an actionable local/CI strategy.

### Scene 1 — Spend native build time where it buys confidence

**Time:** `04:21–05:02`

**Visual:**

Place JVM unit/integration tests under `every edit`. Add focused hint/AOT tests under `every edit / PR`. Place representative native integration/smoke tests under `PR / scheduled / release` depending on project risk. Show a clean CI machine rebuilding from declared inputs.

**Script:**

Developers can run focused native tests while changing hints or native-sensitive integrations, while CI runs a representative native suite at the cadence justified by risk—perhaps every pull request, perhaps scheduled or on release candidates. Caches and prepared toolchains can reduce the cost, but correctness should not depend on one developer's machine state. A fresh CI environment still needs to reproduce the native artifact from declared inputs. If native testing becomes too expensive, first remove duplication and focus on the paths native execution uniquely validates.

**Purpose:**

Provide a cost-aware strategy that preserves native confidence without making every test cycle a native build.

## Where Does Native-specific Testing Hand Off to the General Boot Testing Module?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:02–05:13`

**Visual:**

Draw a boundary around native-specific concerns and place the existing Boot testing toolbox on the other side.

**Script:**

Native testing consumes the application's normal test strategy; it does not become the new owner of `@SpringBootTest`, slices, Testcontainers, or general integration design.

**Purpose:**

Close the video with the testing ownership boundary.

### Scene 1 — Reuse normal tests, add one native question

**Time:** `05:13–05:54`

**Visual:**

List `@SpringBootTest`, slices, web environments, property overrides, dependency replacement, Testcontainers service connections` inside a `testing` module box. Feed representative tests from that box into an `AOT/native validation` box.

**Script:**

The `testing` module owns Boot's ordinary test slices, full-context tests, web environments, test auto-configuration, property overrides, dependency replacement, Testcontainers service connections, and general integration strategy. Native-image reuses those tests and adds one narrower question: does the important behavior still work after AOT processing and native compilation? Keep that AOT/native dimension here, and keep the broader test-design curriculum with its primary owner.

**Purpose:**

Preserve the module boundary while showing how existing tests become native evidence.
