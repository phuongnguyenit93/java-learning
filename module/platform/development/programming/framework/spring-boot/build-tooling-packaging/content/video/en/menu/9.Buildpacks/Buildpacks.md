---
video:
  url: ""
---

# Cloud Native Buildpacks

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

## What Are Builders, Buildpacks, and Run Images?

<!-- VIDEO_SECTION -->

### Scene 1 — Separate the three Buildpacks roles

**Time:** `00:00–00:29`

**Visual:**

Show three labeled blocks: builder image = build environment + lifecycle + buildpacks; buildpack = detection + contributed layers; run image = runtime base. Feed a Boot application into the builder and show an OCI image coming out.

**Script:**

Cloud Native Buildpacks create an OCI image without requiring every application team to author the full Dockerfile. The builder supplies the build environment, lifecycle, and available buildpacks. Individual buildpacks detect application needs and contribute build or runtime layers. The run image supplies the base used by the final application image. Spring Boot integrates with those roles; it does not replace the Buildpacks specification.

**Purpose:**

Give the learner the minimum vocabulary needed to reason about Boot's Buildpacks integration.

## What Part of the Buildpack Lifecycle Does a Boot User Need to Understand?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:29–00:39`

**Visual:**

Turn the builder block into a left-to-right lifecycle timeline.

**Script:**

You do not need to implement the lifecycle, but you do need enough of its order to know where an image build failed.

**Purpose:**

Move from Buildpacks roles to an ordered diagnostic model of the lifecycle.

### Scene 2 — Follow analyze, detect, restore, build, export

**Time:** `00:39–01:09`

**Visual:**

Animate `analyze → detect → restore → build → export`. Under each phase show one evidence cue: prior image state, applicable buildpacks, restored cache, contributed layers, final OCI image/registry.

**Script:**

At a useful learner level, the lifecycle reads previous image state, detects which buildpacks apply, restores reusable layers, builds the required application and runtime content, and exports the image. The Boot task orchestrates this flow and its logs are the first evidence source. A detection failure points toward application input or builder selection; an export failure points much later toward image or registry handling.

**Purpose:**

Provide enough lifecycle ordering to classify failures without teaching Buildpacks internals.

## How Do Buildpack Layers and Caches Support Repeated Builds?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:09–01:15`

**Visual:**

Replay a second build with only application classes changed and highlight which prior layers remain reusable.

**Script:**

The ordered lifecycle also explains why repeated image builds can avoid rebuilding everything from scratch.

**Purpose:**

Connect lifecycle phases to the practical value of layers and caches.

### Scene 3 — Reuse stable runtime and dependency work

**Time:** `01:15–01:45`

**Visual:**

Show build and launch caches plus layers for JDK/runtime, dependencies, and application content. Change one source file, then mark stable layers as reused and the application layer as rebuilt.

**Script:**

Buildpacks separate runtime components and application content into reusable layers and maintain cache state across builds. A source edit should not necessarily force the JDK and every dependency to be rebuilt. But cache reuse is conditional: builder changes, dependency changes, environment changes, or explicit cache cleanup can invalidate it. Measure what was reused from the build logs instead of assuming that “cache enabled” means “cache hit”.

**Purpose:**

Explain cache reuse as observable lifecycle behavior with clear invalidation causes.

## Which Buildpack Inputs Can Spring Boot Customize?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:45–01:53`

**Visual:**

Place a configuration panel in front of the builder with toggles for image name, builder, run image, environment, buildpacks, bindings, caches, network, and publish.

**Script:**

When the defaults do not fit, Boot exposes the Buildpacks inputs an application team most often needs to control.

**Purpose:**

Move from understanding the lifecycle to configuring it through supported Boot inputs.

### Scene 4 — Prefer supported high-level inputs

**Time:** `01:53–02:21`

**Visual:**

Highlight image name, builder/run image, buildpack environment variables, additional buildpacks, bindings, cache configuration, builder network, and registry credentials. Show a stable builder reference beside a floating tag with a warning symbol.

**Script:**

Boot's Gradle and Maven integrations can configure the target image, builder and run image, buildpack environment, extra buildpacks, bindings, caches, network-related settings, and publication credentials. Prefer the highest-level supported input that expresses the requirement. In repeatable pipelines, also control builder evolution deliberately; a floating builder tag can change the JDK or buildpack behavior even when application source did not change.

**Purpose:**

Show the supported customization surface and connect it to reproducible image builds.

## Where Does Buildpack Integration End and Native-Image Semantics Begin?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:21–02:32`

**Visual:**

Fork the Buildpacks output into “JVM image” and “native executable image”, then place a boundary before AOT, closed-world analysis, hints, and native tests.

**Script:**

Buildpacks can participate in more than one runtime strategy, so the command that creates the image does not determine who owns every semantic question behind it.

**Purpose:**

Prepare the ownership handoff from image invocation to native-image semantics.

### Scene 5 — Invocation can be shared while semantics stay separate

**Time:** `02:32–03:15`

**Visual:**

Show `bootBuildImage / build-image` on the packaging side. Add a Gradle callout: applying `org.graalvm.buildtools.native` makes Boot choose the native-oriented default builder and set `BP_NATIVE_IMAGE=true`. Then cross into a separate native-image box containing AOT, hints, closed-world constraints, and compatibility.

**Script:**

Spring Boot can drive a Buildpacks path that produces either an ordinary JVM image or a native executable image. On Gradle, applying the GraalVM Native Image plugin is not merely documentation: Boot reacts by selecting its native-oriented builder default and supplying `BP_NATIVE_IMAGE=true` to the builder. That automatic bridge belongs to build integration. Once the question becomes why AOT is required, whether hints are missing, how closed-world analysis behaves, or how native tests work, ownership moves to the native-image curriculum. The image command is the entry point, not the owner of native semantics.

**Purpose:**

Capture Boot's automatic native Buildpacks bridge while handing AOT, hints, closed-world behavior, and native testing to the native-image owner.
