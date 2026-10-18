---
video:
  url: ""
---

# Packaging Trade-offs and Deployment Handoff

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

## How Do You Choose Between an Executable JAR, WAR, Unpacked Archive, and OCI Image?

<!-- VIDEO_SECTION -->

### Scene 1 — Start from the environment's execution contract

**Time:** `00:00–00:30`

**Visual:**

Show four delivery units connected to matching targets: executable JAR → standalone JVM; WAR → external servlet container; extracted archive → platform using separate files/libraries; OCI image → container platform.

**Script:**

Packaging formats are not maturity levels. Choose the unit that matches how the target environment starts and manages the application. A standalone JVM process commonly consumes an executable JAR, an external servlet container may require a WAR, some platforms benefit from extracted files, and a container platform expects an OCI image. The application code can remain the same while responsibility moves between artifact and platform.

**Purpose:**

Establish packaging choice as an execution-contract decision.

## How Should the Delivery Environment Influence the Packaging Choice?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:30–00:39`

**Visual:**

Turn the four artifact choices into questions: how is it started, updated, cached, observed, and secured?

**Script:**

The format becomes easier to choose when we stop asking what Boot can build and ask what the delivery environment naturally consumes.

**Purpose:**

Move from artifact taxonomy to environment-driven decision criteria.

### Scene 2 — Minimize accidental transformation after build

**Time:** `00:39–01:11`

**Visual:**

Compare two paths: build → final delivery unit → deploy; and build → JAR → ad-hoc repackaging → another image → deploy. Mark each transformation as a place identity or configuration can drift.

**Script:**

Look at how the environment starts, updates, caches, observes, and secures the application. A VM may only need a JAR and JDK; Kubernetes normally consumes an image; a corporate servlet platform may require a WAR. Prefer the path with the fewest accidental transformations. If every deployment converts a JAR to an image, decide explicitly whether that conversion belongs to a central platform or should happen earlier in the Boot build.

**Purpose:**

Connect delivery format to platform behavior and reduce unowned repackaging steps.

## What Is the Boundary Between Boot Packaging and Deployment Operations?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:11–01:18`

**Visual:**

Place the selected JAR/image on a handoff line and reveal deployment actions on the other side.

**Script:**

Once the final unit is chosen and built, ownership changes from creating the artifact to operating it.

**Purpose:**

Establish the build-to-deployment ownership boundary.

### Scene 3 — Promote the exact unit that was verified

**Time:** `01:18–01:42`

**Visual:**

Left: Boot produces JAR checksum or image digest. Right: repository/registry, secret injection, scheduling, traffic routing, rollout, rollback, scaling. Show the same checksum/digest being promoted through environments.

**Script:**

Boot packaging creates the unit to deliver. Deployment infrastructure decides where and when it runs, which secrets it receives, how traffic reaches it, and how rollout or scaling works. Keep that handoff immutable: record the JAR checksum or image digest and promote the same unit. Rebuilding during deployment mixes responsibilities and weakens traceability.

**Purpose:**

Make immutable artifact identity the evidence connecting build output to deployment operations.

## Where Does Normal JVM Packaging Hand Off to the Native-Image Path?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:42–01:49`

**Visual:**

Fork the delivery path into “JVM bytecode + JVM runtime” and “AOT/native analysis → platform-specific executable”.

**Script:**

One packaging branch changes more than the wrapper around the application: native image changes the execution model itself.

**Purpose:**

Prepare the semantic handoff from ordinary JVM packaging to the native-image curriculum.

### Scene 4 — Same image wrapper, different build semantics

**Time:** `01:49–02:14`

**Visual:**

Show a JVM OCI image containing a JVM and Boot application, beside a native OCI image containing a platform-specific executable. Put AOT, hints, closed-world analysis, and native tests in a separate owner box.

**Script:**

This module's normal path produces JVM-oriented artifacts: executable archives or images that still contain a JVM runtime. A native build introduces AOT processing and platform-specific native analysis. The Boot plugin or Buildpacks command may trigger that path, but questions about closed-world constraints, runtime hints, compatibility, and native testing belong to the native-image module.

**Purpose:**

Separate packaging invocation from the distinct semantics of native-image production.

## How Does the End-to-End Boot Build and Packaging Flow Fit Together?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:14–02:20`

**Visual:**

Zoom out to a blank horizontal pipeline spanning dependencies, development, packaging, image creation, and deployment handoff.

**Script:**

We can now reconnect every concept in this module into one build-and-delivery chain.

**Purpose:**

Synthesize earlier videos into one ordered mental model.

### Scene 5 — Read the complete JVM-oriented delivery chain

**Time:** `02:20–02:51`

**Visual:**

Reveal sequentially: declare dependencies → align with Boot BOM → apply Boot plugin → `bootRun / spring-boot:run` → `bootJar / bootWar / repackage` → optional layering/extraction → optional Buildpacks OCI image → deployment handoff.

**Script:**

The flow begins with declared dependencies and the Boot compatibility baseline. The plugin then supports fast development execution, executable packaging, optional layer or extraction choices, and optionally a Buildpacks image. Each stage answers a different question and produces evidence for the next one. Keeping the stages separate is what lets a team locate failures instead of treating every unsuccessful Gradle or Maven command as the same build problem.

**Purpose:**

Give the learner a single ordered map of the module's concepts and their outputs.

## How Do You Classify a Failure Across Plugin, Packaging, Image, and Deployment Boundaries?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:51–02:59`

**Visual:**

Keep the end-to-end pipeline and place green checkpoints after each successful boundary.

**Script:**

That pipeline also becomes a troubleshooting tool: the latest successful checkpoint tells us which subsystem had already completed its responsibility.

**Purpose:**

Turn the synthesized flow into a failure-classification method.

### Scene 6 — Diagnose from the latest successful boundary

**Time:** `02:59–03:36`

**Visual:**

Highlight cases in order: dependency/plugin application fails; compile succeeds but `bootJar/repackage` fails; JAR runs but `bootBuildImage` fails; image builds but publish/pull fails; artifact launches but ApplicationContext fails. End by comparing the built digest with the deployed digest.

**Script:**

If dependency resolution or plugin application fails, start with build setup. If compilation succeeds but executable packaging fails, inspect Boot packaging and main-class configuration. If the JAR runs but the image task fails, inspect Buildpacks, builder, daemon, or image configuration. If the image builds but registry operations fail, move to registry or deployment infrastructure. And if the artifact launches but the ApplicationContext fails, the process has crossed into application runtime. Always verify the exact checksum or digest that crossed the handoff.

**Purpose:**

Finish the module with an evidence-based troubleshooting method spanning build, packaging, image, deployment, and runtime boundaries.
