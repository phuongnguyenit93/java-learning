---
video:
  url: ""
---

# Why Spring Boot Native Images Need AOT

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

## What Is a Spring Boot Native Image?

<!-- VIDEO_SECTION -->

### Scene 1 — From Boot application to native executable

**Time:** `00:00–00:38`

**Visual:**

Animate one application through four labeled stages: `Spring Boot app` → `Spring AOT` → `GraalVM Native Image` → `platform-specific executable`. Keep a normal `java -jar` path visible below for contrast, but dim it until the next section.

**Script:**

A Spring Boot native image is not a JAR with a different launcher. Spring first prepares the application ahead of time, then GraalVM analyzes that prepared application and compiles a platform-specific executable. At runtime we start that executable directly instead of booting a normal JVM and loading the application JAR. That changed deployment form is the reason the build has to understand much more of the application up front.

**Purpose:**

Establish the complete native build/runtime shape before introducing the restrictions that make AOT necessary.

## Why Is Normal Dynamic Boot Startup Not Enough for Native Compilation?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:38–00:49`

**Visual:**

Split the screen: on the left, a JVM discovers classes, conditions, resources, and proxies during startup; on the right, the native compiler asks for those same facts before the executable exists.

**Script:**

The deployment form changed, so the timing of discovery changes too. The JVM can postpone many decisions until startup; native compilation needs enough evidence before that runtime even exists.

**Purpose:**

Move from the definition of a native image to the specific mismatch between dynamic Spring startup and closed-world compilation.

### Scene 1 — Runtime discovery must become build-time evidence

**Time:** `00:49–01:27`

**Visual:**

Reveal five JVM-era behaviors one at a time—classpath scanning, conditional beans, reflection, resource lookup, generated proxies—then move each card leftward across a boundary labeled `AOT/build time`.

**Script:**

A normal Spring application can inspect the classpath, evaluate conditions, discover reflective access, open resources by name, and create proxies while the process is already running. GraalVM Native Image cannot assume that open-ended runtime discovery will still be available. Spring AOT bridges the gap by preparing the application model earlier and by describing dynamic behavior that static analysis cannot infer. The issue is not that Spring is “too dynamic”; it is that the native build needs those dynamic assumptions made explicit soon enough to preserve them.

**Purpose:**

Explain why AOT exists as an adaptation layer rather than presenting native compilation as a simple compiler switch.

## How Does a Native Executable Differ from a Regular JVM Deployment?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:27–01:38`

**Visual:**

Collapse the discovery cards into two deployment columns labeled `JVM form` and `Native form`.

**Script:**

Once discovery moves earlier, the two runtime forms stop being equivalent. Compare what each deployment carries and what work remains for startup.

**Purpose:**

Connect the AOT motivation to the concrete deployment/runtime consequences the learner will observe.

### Scene 1 — Bytecode runtime versus compiled runtime

**Time:** `01:38–02:16`

**Visual:**

Show `JAR + JVM` on the left with arrows for class loading and JIT optimization. On the right show `build-time analysis + native compilation → executable for OS/architecture`, with the runtime support embedded in the produced binary.

**Script:**

The JVM form ships bytecode and depends on a JVM at the destination. Class loading, JIT compilation, reflection, and several framework decisions can remain runtime concerns. The native form moves analysis and compilation into the build and produces machine code for a particular target platform together with the runtime support selected by native-image. The same business application can support both forms, but “works on the JVM” does not prove that every reflective, resource, or dependency assumption is visible to the native build.

**Purpose:**

Teach both sides of the JVM/native comparison so later compatibility failures have a clear mental model.

## What Runtime Benefits Can Native Images Provide?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:16–02:27`

**Visual:**

Move from the two deployment columns to a small measurement dashboard with cold-start time, resident memory, throughput, and tail latency.

**Script:**

Doing more work during the build is only worthwhile if it solves an operational problem. The next question is which runtime measurements can actually improve.

**Purpose:**

Shift from mechanism to measurable value rather than assuming native is inherently better.

### Scene 1 — Measure startup and memory, not slogans

**Time:** `02:27–03:08`

**Visual:**

Plot illustrative, unlabeled comparison bars for cold startup and memory, then add warning badges beside throughput and warm steady-state latency saying `measure your workload`.

**Script:**

Native executables are especially attractive when cold-start latency and memory footprint matter. Moving framework analysis and compilation out of startup can make a native process start much faster, and it can often use less memory for comparable service workloads. That can help scale-to-zero services, short-lived workers, command applications, or dense deployments. But this is not a promise that every metric improves: long-running JVM workloads can benefit from JIT optimization and mature runtime tooling. Compare equivalent configuration and real load before deciding.

**Purpose:**

Preserve the startup/memory benefit while keeping the Knowledge caveat that native economics must be measured against the real workload.

## What Extra Build and Compatibility Costs Come with Native Images?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:08–03:19`

**Visual:**

Keep the measurement dashboard on the left and open a build-cost ledger on the right.

**Script:**

The runtime can become cheaper while the delivery pipeline becomes more expensive. That trade is the other half of the native decision.

**Purpose:**

Balance the runtime-benefit section with the build and compatibility costs that must pay for those gains.

### Scene 1 — The cost moves into build and validation

**Time:** `03:19–03:59`

**Visual:**

Fill the ledger with `longer native build`, `CPU / memory`, `target-specific binary`, `hints / metadata`, `dependency compatibility`, and `native-specific tests`. Highlight the last three as recurring engineering cost, not one-time setup.

**Script:**

Native compilation takes longer and consumes substantial CPU and memory. The output is platform-specific, so release pipelines need a target strategy. Dynamic behavior—reflection, resources, proxies, serialization, JNI, generated code—and third-party libraries add a compatibility dimension that the ordinary JVM path may not expose. That means hint maintenance, dependency investigation, and focused native tests become part of the delivery cost. Native is valuable when the runtime gains justify that recurring cost, not because it is a mandatory modernization step.

**Purpose:**

Make the adoption decision economic and operational, not merely technical.

## What Does This Module Own, and What Belongs to GraalVM or Build Tooling?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:59–04:10`

**Visual:**

Turn the cost ledger into an ownership map with `native-image` in the center and five neighboring boxes around it.

**Script:**

Several of those costs touch other curricula. We need a boundary map so this module explains the native integration without swallowing GraalVM, build tooling, testing, or Java reflection itself.

**Purpose:**

Close the introductory video by defining where deeper questions should be handed off.

### Scene 1 — Keep the native integration story, hand off the internals

**Time:** `04:10–04:53`

**Visual:**

Center: `native-image = Spring AOT + closed-world consequences + Runtime Hints + native build/test workflow + readiness`. Around it: `GraalVM internals`, `build-tooling-packaging`, `testing`, `application-runtime`, and `Java/Spring Framework reflection/proxy/class loading`; animate arrows from a sample question to its owner.

**Script:**

This module owns the Spring Boot application story for AOT and native execution: how Spring prepares the application, what closed-world execution changes, how Runtime Hints describe dynamic access, how supported native build and test paths fit together, and how to decide whether native is ready. GraalVM owns compiler and low-level runtime internals. `build-tooling-packaging` owns the general Maven, Gradle, Buildpacks, and OCI curriculum. `testing` owns the normal Boot test model; `application-runtime` owns the usual lifecycle; Java and Spring Framework own the underlying reflection, proxy, and class-loading semantics. We will cross those boundaries only when native integration needs them.

**Purpose:**

Give the learner a durable ownership map that prevents later sections from duplicating neighboring modules.
