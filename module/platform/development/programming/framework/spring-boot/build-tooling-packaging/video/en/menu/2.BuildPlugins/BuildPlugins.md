---
video:
  url: ""
---

# Spring Boot Gradle and Maven Plugins

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

## What Do the Spring Boot Gradle and Maven Plugins Add?

<!-- VIDEO_SECTION -->

### Scene 1 — Two adapters around the same Boot outcomes

**Time:** `00:00–00:43`

**Visual:**

Split the screen into Gradle and Maven. Under Gradle expand the Java-plugin reaction into `bootJar`, `bootRun`, `bootBuildImage`, `assemble → bootJar`, `plain.jar`, and development/production runtime configurations. Under Maven show plugin goals plus the parent-provided `repackage` execution. Merge both lanes into run, executable archive, and OCI image outcomes.

**Script:**

Spring Boot integrates with two different build models. With Gradle's Java plugin present, Boot reacts to the existing model: it registers tasks such as `bootJar`, `bootRun`, and `bootBuildImage`, wires `assemble` to the executable archive, gives the ordinary JAR a `plain` classifier, and creates development/production runtime configurations used by Boot's launch and packaging paths. Maven exposes goals instead of Gradle tasks; when the project uses `spring-boot-starter-parent`, the parent can also preconfigure the `repackage` execution. The mechanics differ, but both paths adapt the underlying build system to Boot delivery outcomes rather than replacing it.

**Purpose:**

Show the concrete integration each plugin adds while preserving Gradle and Maven as the underlying build engines.

## Which Run, Package, and Image Tasks Belong to the Boot Plugins?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:43–00:50`

**Visual:**

Keep the two build lanes on screen and replace the generic outcomes with exact task and goal names.

**Script:**

With the common outcomes established, we can attach the names developers actually see in builds and logs.

**Purpose:**

Bridge from conceptual plugin roles to concrete Boot operations.

### Scene 2 — Match commands to outputs

**Time:** `00:50–01:17`

**Visual:**

Show a table: Gradle `bootRun`, `bootJar / bootWar`, `bootBuildImage`; Maven `spring-boot:run`, `repackage`, `build-image`. Add arrows from each row to process, archive, or image output.

**Script:**

Think about these operations by the output they create. `bootRun` and Maven's run goal start a development process. `bootJar`, `bootWar`, and `repackage` create executable Boot archives. `bootBuildImage` and `build-image` drive the Buildpacks image path. Each one consumes normal build inputs, so a missing class or unresolved dependency still has to be fixed in the underlying build first.

**Purpose:**

Give the learner a compact task-to-output map and reinforce the dependency on normal build inputs.

## What Remains the Responsibility of Gradle or Maven?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:17–01:27`

**Visual:**

Zoom out from the Boot task table to reveal repositories, source compilation, dependency resolution, incremental work, and project structure around it.

**Script:**

Those Boot operations sit inside a much larger build model. Knowing which layer owns a failure prevents the plugin from becoming the default suspect.

**Purpose:**

Shift attention from Boot-specific commands to the build-system responsibilities they depend on.

### Scene 3 — Diagnose at the build-system boundary

**Time:** `01:27–01:58`

**Visual:**

Present two columns. “Build tool” contains repository credentials, compiler configuration, dependency resolution, task/lifecycle ordering. “Boot integration” contains `BOOT-INF`, main-class discovery, executable layout, and image task configuration.

**Script:**

Gradle and Maven still compile the source, resolve dependencies, model the project, and decide how their tasks or lifecycle phases execute. A useful diagnostic question is whether the same problem would exist in a plain Java project. If repository access or compilation is broken, start with the build tool. If the failure is about `bootJar`, `BOOT-INF`, `repackage`, or `bootBuildImage`, then Boot integration is the better boundary to inspect.

**Purpose:**

Teach a practical ownership test for separating generic build failures from Boot-specific failures.

## How Should You Reason About the Gradle and Maven Integration Paths?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:58–02:04`

**Visual:**

Replace the ownership columns with two distinct road maps labelled “Gradle task model” and “Maven lifecycle/goal model”, both ending at the same executable JAR icon.

**Script:**

The final trap is assuming that equivalent outcomes imply identical configuration models. They do not.

**Purpose:**

Prepare the learner to preserve build-tool-specific mechanics while comparing common Boot intent.

### Scene 4 — Compare intent, not syntax

**Time:** `02:04–02:28`

**Visual:**

Highlight `bootJar` in the Gradle lane and `package + repackage` in the Maven lane. Add a label “same Boot archive model, different build integration”.

**Script:**

Gradle expresses executable packaging as specialized tasks such as `bootJar`. Maven commonly lets the normal package lifecycle create an archive and then applies `repackage`. Do not force these into a fake one-to-one syntax map. Learn the Boot intent first, then use the official integration style of the build tool the project already chose.

**Purpose:**

End with a comparison method that respects Gradle and Maven differences while preserving the shared Boot model.
