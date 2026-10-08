---
video:
  url: ""
---

# Building OCI Images with Spring Boot

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

## What Does `bootBuildImage` or Maven `build-image` Produce?

<!-- VIDEO_SECTION -->

### Scene 1 — From Boot build input to an OCI application image

**Time:** `00:00–00:27`

**Visual:**

Show `bootJar` ending at an executable JAR and, in a separate lane, `bootBuildImage / build-image` ending at an OCI image reference. Label the second lane “Cloud Native Buildpacks”.

**Script:**

`bootBuildImage` and Maven's `build-image` goal use Cloud Native Buildpacks to create an OCI-compatible application image. The image contains the application plus runtime layers and launch metadata selected through the builder and buildpacks. That is a different delivery unit from an executable JAR: the final handoff is an image reference that a container platform can pull and run.

**Purpose:**

Distinguish the OCI image output from Boot's executable archive output.

## How Does a Boot Application Become an OCI Image?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:27–00:33`

**Visual:**

Expand the “Buildpacks” label into a pipeline between application input and final OCI image.

**Script:**

The image task does not translate a hidden Dockerfile. It drives the Buildpacks lifecycle.

**Purpose:**

Move from the output artifact to the mechanism that assembles it.

### Scene 2 — Buildpacks assemble runtime and application layers

**Time:** `00:33–01:03`

**Visual:**

Animate `Boot application input → builder + buildpacks → detect → reusable layers → run image → OCI image`. At the end reveal launch process metadata and a container start arrow.

**Script:**

The builder receives the Boot application input, detects the required buildpacks, creates or reuses application and runtime layers, combines them with the run image, and exports an OCI image. Buildpacks also contribute the process metadata used to launch the application. That standardized path is why an application team can build a runnable image without reproducing Boot's complete classpath launch command in a Dockerfile.

**Purpose:**

Show the application-to-image path and where launch metadata comes from.

## Why Does Image Building Need Access to a Docker Daemon or Supported Docker-Compatible Engine?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:03–01:13`

**Visual:**

Place the Gradle/Maven process on one side and a Docker daemon/container engine on the other, connected by a socket or remote endpoint.

**Script:**

The plugin orchestrates the build, but image creation still needs a container engine that can run the builder and store or publish image layers.

**Purpose:**

Introduce the container-engine connection as a build prerequisite distinct from application runtime.

### Scene 3 — Diagnose the three external connections separately

**Time:** `01:13–01:44`

**Visual:**

Show three arrows: build tool → Docker daemon; builder container → external dependency sources; image task → registry. Add labels for `DOCKER_HOST`, Docker context/TLS configuration, and registry credentials.

**Script:**

In Spring Boot 3.3, the Buildpacks image task needs access to a Docker daemon through the supported Docker connection model. In CI that connection must be configured explicitly; a developer's local Docker Desktop setup does not magically exist on a remote runner. Keep three paths separate when troubleshooting: reaching the daemon, reaching dependencies from inside the builder, and reaching the registry for pull or publish operations.

**Purpose:**

Give the learner a concrete connection model for classifying image-build infrastructure failures.

## How Are Image Naming, Tags, and Publication Configured at the Boot Integration Boundary?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:44–01:51`

**Visual:**

Show a successfully built local image, then overlay a full image name with registry host, repository, tag, and digest.

**Script:**

Once the image builds, its identity becomes the contract between the build and downstream delivery systems.

**Purpose:**

Move from image construction to naming, publication, and traceability.

### Scene 4 — Give downstream systems an immutable image identity

**Time:** `01:51–02:31`

**Visual:**

Show Boot configuration setting image name, additional tags, publish flag, and registry credentials. Add default-name callouts: Gradle `docker.io/library/${project.name}:${project.version}` and Maven the analogous artifactId/version form. Mark publish as opt-in and move credential storage/rotation into a CI-secret box. Then compare a mutable tag with an immutable digest.

**Script:**

Boot derives a usable image name by default—Gradle from project name/version and Maven from artifactId/version—and lets the build override that name or add tags. Publishing is opt-in; when enabled, the plugin can pass registry credentials, but CI or the build environment should remain the authority for storing and rotating those secrets. Registry retention, signing, promotion policy, and tag governance stay downstream. Whatever naming convention is used, delivery should retain an immutable digest association so the promoted image is exactly the one the build produced.

**Purpose:**

Connect Boot's defaults, opt-in publication, and credential handoff to traceable image delivery without absorbing registry governance.
