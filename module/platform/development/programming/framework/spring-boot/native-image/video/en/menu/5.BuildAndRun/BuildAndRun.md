---
video:
  url: ""
---

# Build and Run a Native Spring Boot Application

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

## What Is the Supported Boot Native Build Workflow?

<!-- VIDEO_SECTION -->

### Scene 1 — Two supported production paths, one AOT boundary

**Time:** `00:00–00:41`

**Visual:**

Draw one shared start: `Boot application + build-time environment` → `Spring AOT`. Split after AOT into `GraalVM Native Build Tools → executable` and `Cloud Native Buildpacks → OCI image containing native executable`.

**Script:**

Spring Boot gives us two practical production paths for native delivery. GraalVM Native Build Tools can produce a native executable directly through Maven or Gradle. Cloud Native Buildpacks can build an OCI image whose runtime payload is native rather than JVM-based. The important common point is earlier in the diagram: both paths rely on Spring AOT preparing the application before GraalVM performs native-image analysis and compilation.

**Purpose:**

Establish the shared semantic pipeline before comparing build-tool entry points.

## How Do Maven and Gradle Integrate GraalVM Native Build Tools?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:41–00:52`

**Visual:**

Keep the shared AOT pipeline and replace the two output boxes with side-by-side Maven and Gradle terminals.

**Script:**

The semantics are the same, but Maven and Gradle expose different entry points and wiring. Seeing both prevents “native support” from becoming Maven-only knowledge.

**Purpose:**

Move from the platform-neutral workflow to the concrete supported build integrations in both build tools.

### Scene 1 — Maven parent profile and direct native compile

**Time:** `00:52–01:27`

**Visual:**

Show `mvn -Pnative native:compile`. Above it, a small `spring-boot-starter-parent` card expands to `process-aot + Native Build Tools defaults + reachability metadata support`.

**Script:**

For a Maven application inheriting from `spring-boot-starter-parent`, Boot provides a `native` profile. That profile wires `process-aot` into the build and supplies sensible Native Build Tools configuration, so `mvn -Pnative native:compile` can compile the AOT-prepared application. A Maven project that does not use the parent cannot assume `-Pnative` exists; it must configure the equivalent Boot AOT and native plugin executions explicitly.

**Purpose:**

Explain what the Maven native profile actually contributes and where its convenience boundary ends.

### Scene 2 — Gradle wires `processAot` before `nativeCompile`

**Time:** `01:27–02:04`

**Visual:**

Show a Gradle task graph: `processAot` → generated AOT sources/resources/classes → `nativeCompile`. Highlight `org.graalvm.buildtools.native` beside the Spring Boot plugin, then show `gradle nativeCompile` in a terminal.

**Script:**

With Gradle, apply GraalVM Native Build Tools alongside Spring Boot. Boot integrates its `processAot` work with the native task graph, so `nativeCompile` consumes the generated AOT assets instead of compiling an ordinary Boot runtime model. The command is easy to remember, but the important concept is task dependency: prepare Spring's application model first, then let the GraalVM plugin compile the enriched application inputs.

**Purpose:**

Make the Gradle task dependency concrete so learners can explain why `nativeCompile` consumes Spring AOT output.

## How Does AOT Output Flow into Native Compilation?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:04–02:15`

**Visual:**

Merge Maven and Gradle back into one task graph and zoom in on the files passed from AOT to native compilation.

**Script:**

Both build tools converge on the same handoff. The next step is to see exactly what the native compiler receives.

**Purpose:**

Move from build-tool commands back to the shared technical contract between Spring AOT and GraalVM.

### Scene 1 — Feed generated code, classes, and metadata into analysis

**Time:** `02:15–02:54`

**Visual:**

Show generated Java compiling into classes, generated proxy bytecode joining the classpath, and native-image configuration under `META-INF/native-image` entering the GraalVM analysis input.

**Script:**

Spring AOT generated Java is compiled, generated classes are added to the application inputs, and native-image resources carry the reachability requirements GraalVM needs. The compiler analyzes this prepared classpath, not just the original source or a normal dynamic Boot startup. This ordering is the supported Spring native pipeline. If required AOT assets are missing, invoking native-image directly does not magically recreate Spring's preparation step.

**Purpose:**

Make the build handoff explicit so native failures can be traced back to missing preparation rather than guessed at compiler flags.

## What Does the Platform-specific Native Executable Contain?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:54–03:05`

**Visual:**

Shrink the compile inputs into a finished binary and stamp it with `linux-amd64` as an example target label.

**Script:**

Once GraalVM finishes, the output no longer has the portability model of JVM bytecode. It is a native artifact for a target platform.

**Purpose:**

Connect native compilation to release and deployment implications.

### Scene 1 — A binary for a target, not portable JVM bytecode

**Time:** `03:05–03:42`

**Visual:**

Open the native artifact as a composition diagram: machine code, selected runtime support, included resources/metadata. Place a crossed-out `one binary for every OS/architecture` label beside it.

**Script:**

The native executable contains machine code plus the runtime support and resources retained by native-image analysis. It is built for a target operating system and architecture rather than remaining portable JVM bytecode. That means release engineering needs a strategy for each supported target; native-image is not normally a “build once, run everywhere” replacement for the JVM. From the application's point of view, Spring beans, web integrations, and business code still run—just behind a different deployment boundary.

**Purpose:**

Teach the platform-specific nature of the output without diving into GraalVM compiler internals.

## How Does Spring Boot's Buildpacks Integration Produce an OCI Image with a Native Executable?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:42–03:53`

**Visual:**

Place the native executable inside a container outline, then rewind to a Buildpacks builder so the learner sees that the builder can perform the native compilation itself.

**Script:**

Sometimes the delivery contract is an OCI image rather than a bare binary. Boot can keep the native runtime while changing only the packaging path.

**Purpose:**

Separate native runtime choice from container delivery choice.

### Scene 1 — Let the builder provide the native toolchain

**Time:** `03:53–04:35`

**Visual:**

Show `mvn -Pnative spring-boot:build-image` and Gradle `bootBuildImage` in two terminals. Behind them, animate a Paketo builder detecting the app, running AOT/native compilation, and exporting an OCI image with a native executable layer.

**Script:**

Boot's `build-image` and `bootBuildImage` integrations can select a native build path when the project is configured for it. With Maven's parent-provided native profile, `mvn -Pnative spring-boot:build-image` activates that path. With Gradle, applying GraalVM Native Build Tools lets `bootBuildImage` build the native application form. The builder supplies the native toolchain, performs AOT and native compilation, and exports an OCI image containing the native executable. Generic builder, cache, registry, and publishing mechanics still belong to `build-tooling-packaging`.

**Purpose:**

Show how native compilation fits inside Boot's Buildpacks path while preserving the neighboring ownership boundary.

## How Does Running the Native Executable Differ from `java -jar`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:35–04:46`

**Visual:**

Put `java -jar app.jar` and `./app` side by side and move a single environment-variable card toward both processes.

**Script:**

The artifacts launch differently, but operational configuration does not disappear. What changes is which structural decisions are still allowed to move at runtime.

**Purpose:**

Connect the build result back to day-to-day process startup and configuration.

### Scene 1 — Start an OS process, keep runtime configuration within the AOT boundary

**Time:** `04:46–05:24`

**Visual:**

Run `java -jar app.jar` in one terminal and `./app` in another. Show both reading `DB_URL`, then overlay `bean structure prepared by AOT` only on the native path.

**Script:**

`java -jar` starts a JVM and gives Boot a bytecode and classpath runtime. A native executable is launched directly as an operating-system process—or indirectly by starting the OCI image that contains it. Environment variables and external configuration still matter in both forms. The native difference is the AOT boundary: those runtime inputs can supply values, but they cannot assume that a bean structure already fixed during AOT will be rebuilt into a contradictory shape.

**Purpose:**

Teach the runtime launch difference without incorrectly implying that native applications lose external configuration.

## Where Does Native-image Ownership Hand Off to Build Tooling and Packaging?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:24–05:35`

**Visual:**

Turn the two terminals into an ownership diagram: `native semantics` on the left, `general build/packaging mechanics` on the right.

**Script:**

We used Maven, Gradle, and Buildpacks because they are evidence for the native path. Their full mechanics remain a different curriculum.

**Purpose:**

Close the build workflow without duplicating build-tooling-packaging.

### Scene 1 — Keep commands as evidence, hand generic mechanics to the build module

**Time:** `05:35–06:12`

**Visual:**

List native-image ownership: AOT meaning, closed-world constraints, native compilation handoff, native runtime choice. Beside it list build-tooling ownership: Boot plugin model, dependency alignment, archive tasks, Buildpacks lifecycle, OCI names/tags/publish/credentials.

**Script:**

This module owns what the native steps mean for the Spring application: the AOT-prepared model, the closed-world constraints, and the handoff into a native executable or native OCI image. `build-tooling-packaging` owns the general Boot plugin model, dependency management, archive tasks, Buildpacks lifecycle, OCI naming and publishing, and generic Maven or Gradle mechanics. We keep the native commands here because they prove the integration path; we do not turn them into a second build-tool curriculum.

**Purpose:**

Make the cross-module boundary explicit after showing enough build evidence to use the native workflow correctly.
