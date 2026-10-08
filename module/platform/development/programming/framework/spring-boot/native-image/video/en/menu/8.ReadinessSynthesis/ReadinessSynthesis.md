---
video:
  url: ""
---

# End-to-End Native Image Readiness

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

## How Does the Full Boot Native-image Journey Fit Together?

<!-- VIDEO_SECTION -->

### Scene 1 — One chain of evidence from Boot model to deployment

**Time:** `00:00–00:45`

**Visual:**

Animate a single application through seven checkpoints: normal Boot model → native goal/build environment → Spring AOT → Runtime Hints/reachability metadata → GraalVM native compilation → focused native tests → executable or OCI image. Put a small evidence icon under each checkpoint.

**Script:**

Treat native readiness as one chain, not a bag of unrelated tools. We start with a normal Boot application and a deliberate native goal. The build environment becomes input to Spring AOT. AOT prepares the bean model and generated assets. Runtime Hints and dependency metadata preserve dynamic behavior. GraalVM compiles the closed-world executable. Focused native tests exercise the paths that can diverge, and only then do we deliver a binary or OCI image. When something fails, return to the earliest link whose assumptions are not proven.

**Purpose:**

Give the learner one end-to-end model that connects all previous menus before applying a readiness checklist.

## What Should Be Checked before Committing to Native Deployment?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–00:56`

**Visual:**

Freeze the seven checkpoints and turn them into columns on a readiness board with empty checkboxes.

**Script:**

A successful local compile is only one checkbox. Production readiness also depends on reproducibility, compatibility, tests, targets, and operational economics.

**Purpose:**

Move from conceptual flow to a concrete go/no-go review without reducing readiness to “native image built once.”

### Scene 1 — Use a delivery checklist, not a compiler-success badge

**Time:** `00:56–01:45`

**Visual:**

Check items one by one: measurable startup/memory goal; supported JDK/GraalVM/Boot toolchain; build-time profiles/properties understood; reflection/resources/proxies covered; critical dependencies native-ready; native-sensitive paths tested; CI capacity; target OS/architecture plan; acceptable diagnostics/observability; JVM fallback/rollback understood.

**Script:**

Before native becomes the production delivery form, ask ten practical questions. Is the runtime goal measurable? Is the JDK, GraalVM, and Boot toolchain reproducible? Are build-time profiles and structural properties understood? Do dynamic reflection, resources, and proxies have supported metadata? Are critical dependencies native-ready? Do native-sensitive paths have tests? Can CI afford the build? Is the target OS and architecture strategy explicit? Are diagnostics and observability acceptable? And, where it matters, is the JVM fallback or rollback path understood? That turns “it builds on my machine” into an operational decision.

**Purpose:**

Restore the complete readiness checklist that was previously truncated and frame it as evidence for deployment commitment.

## How Do You Locate a Failure across AOT, Closed-world, Hints, Build, and Dependencies?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:45–01:56`

**Visual:**

Convert the checklist board into a numbered diagnostic ladder from JVM to the failing native code path.

**Script:**

When a checkbox fails, diagnose forward from the cheapest, most familiar boundary. Each successful rung rules out a layer.

**Purpose:**

Introduce a complete failure-location sequence rather than the truncated two-item list from the previous script.

### Scene 1 — Walk the seven boundaries in order

**Time:** `01:56–02:42`

**Visual:**

Highlight each rung: 1 JVM build/run; 2 JVM tests; 3 AOT processing; 4 generated AOT/hint assets; 5 native compilation; 6 native startup; 7 failing native-specific code path. Show the likely owner next to steps 3, 5, and 7.

**Script:**

Start with the regular JVM build and run. Then confirm the normal JVM tests. Next run AOT processing and inspect the generated AOT and hint assets. Only after those boundaries are sound should native compilation become the focus. Then verify native startup and finally the specific code path that fails natively. Failure during AOT points toward the prepared Spring model. Failure during native compilation points toward toolchain, unsupported constructs, or reachability inputs. A failure only on a running native dynamic path points more strongly toward hints, dependency metadata, or an environment difference.

**Purpose:**

Give the learner a complete, ordered diagnostic ladder that narrows ownership without defaulting to reflection configuration.

## How Do You Choose a Native Executable, Native Container Image, or JVM Deployment?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:53`

**Visual:**

Replace the ladder with a 2×2 matrix: runtime form on one axis (`JVM` / `native`) and delivery form on the other (`direct artifact` / `OCI image`).

**Script:**

Once the application is understood, separate two decisions that are often mixed together: which runtime model to use, and how to package that runtime for the deployment platform.

**Purpose:**

Prevent native-vs-JVM reasoning from being confused with container-vs-bare-binary packaging.

### Scene 1 — Choose runtime economics first, packaging contract second

**Time:** `02:53–03:36`

**Visual:**

Fill the matrix: native executable = native runtime/direct process; native OCI = same native runtime/container contract; JVM JAR = JVM runtime/direct Java launch; JVM OCI = JVM runtime/container contract. Add decision arrows: deployment platform chooses packaging; startup/memory/build/compatibility chooses runtime.

**Script:**

A native executable and a native OCI image share the native runtime model; the difference is how the platform receives and starts it. A JVM JAR and a JVM OCI image share the JVM runtime model. So choose direct binary versus image mainly from the deployment contract. Choose native versus JVM from runtime economics, build cost, dependency compatibility, diagnostics, and operational requirements. Containers do not make a JVM application native, and using native compilation does not force you to deliver a bare executable.

**Purpose:**

Clarify the runtime-form versus packaging-form distinction that drives the final delivery choice.

## Which Generated Assets, Tests, and Compatibility Evidence Should Drive the Decision?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:36–03:47`

**Visual:**

Connect the decision matrix to an evidence loop containing generated sources/hints, build logs, tests, dependency docs, and runtime measurements.

**Script:**

The decision should stay revisable. Generated output, tests, dependency support, and production-like measurements form a feedback loop as the application evolves.

**Purpose:**

Move from a one-time delivery choice to ongoing native-readiness maintenance.

### Scene 1 — Maintain readiness as evidence

**Time:** `03:47–04:29`

**Visual:**

Cycle through: inspect generated AOT/hints → native build logs → focused native tests → dependency support documentation → startup/memory/load metrics → decision. Show one hint workaround being added with a test, then later deleted after a dependency upgrade.

**Script:**

Use generated AOT sources and hints, native build logs, focused native tests, dependency support documentation, and production-like startup and memory measurements as one feedback loop. When an explicit application hint fixes a failure, add a test that proves the required registration. When a dependency upgrade supplies proper metadata, remove the stale workaround. If representative measurements show that native provides no material value, keep the JVM path instead of preserving complexity because the team already invested in it.

**Purpose:**

Define native readiness as maintained evidence rather than a one-time successful compile.

## Which Neighboring Module Owns the Next Layer of Detail?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:29–04:40`

**Visual:**

Place the evidence loop in the center and expand six labeled ownership arrows around it.

**Script:**

When the investigation goes deeper, the native module should hand the question to the owner of that mechanism rather than duplicate its curriculum.

**Purpose:**

End the module with a practical navigation map for follow-up learning and diagnosis.

### Scene 1 — Route deeper questions to the primary owner

**Time:** `04:40–05:24`

**Visual:**

Map six question types: conditional beans/auto-config → `auto-configuration`; normal lifecycle/events → `application-runtime`; Maven/Gradle/Buildpacks/OCI delivery → `build-tooling-packaging`; general test slices/full-context strategy → `testing`; reflection/proxy/class-loader semantics → `Java / Spring Framework`; native compiler internals → `GraalVM`. Keep `native-image` in the center owning integration and readiness.

**Script:**

If the question is why a conditional auto-configuration matched, go to `auto-configuration`. For normal lifecycle and runtime behavior, go to `application-runtime`. For Maven, Gradle, Buildpacks, and OCI delivery mechanics, go to `build-tooling-packaging`. For general Boot testing, go to `testing`. Reflection, proxies, and class loading belong to Java and Spring Framework; low-level native compiler behavior belongs to GraalVM. This module keeps the end-to-end integration story coherent, then hands deeper mechanics to their primary owners.

**Purpose:**

Close the native-image curriculum with an explicit ownership map and no leaked README markers or unfinished narration.
