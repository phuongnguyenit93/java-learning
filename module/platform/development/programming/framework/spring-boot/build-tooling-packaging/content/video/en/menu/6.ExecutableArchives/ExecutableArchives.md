---
video:
  url: ""
---

# Executable Archives and Spring Boot Loader

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

## Why Does Spring Boot Use Nested Archives?

<!-- VIDEO_SECTION -->

### Scene 1 — Keep dependencies distinct inside one deliverable

**Time:** `00:00–00:27`

**Visual:**

Show three dependency JARs and application classes. First flatten them into one archive and overlay collision warnings; then reset and place the dependency JARs intact inside one Boot executable archive.

**Script:**

A Boot application may depend on dozens of JARs. Flattening all of those files together destroys useful artifact boundaries and can create resource or metadata collisions. Spring Boot keeps dependency JARs nested, so the application can still be distributed as one file while its dependencies remain recognizable units. That layout is the reason a Boot-specific loader is needed later.

**Purpose:**

Motivate nested archives as a packaging design before showing their physical layout.

## How Are `BOOT-INF` and `WEB-INF` Laid Out?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:27–00:33`

**Visual:**

Zoom into the executable archive and turn it into a file-tree view.

**Script:**

The nested-archive idea becomes concrete when we inspect the directories Boot actually writes.

**Purpose:**

Bridge the packaging concept to observable archive evidence.

### Scene 2 — Read the archive tree as diagnostic evidence

**Time:** `00:33–00:58`

**Visual:**

Show a JAR tree with `BOOT-INF/classes` and `BOOT-INF/lib/*.jar`. Beside it show a WAR tree with `WEB-INF/classes`, `WEB-INF/lib`, and `WEB-INF/lib-provided`. Highlight one expected class and one dependency.

**Script:**

In an executable JAR, application classes and resources live under `BOOT-INF/classes`, while runtime dependency JARs live under `BOOT-INF/lib`. A WAR uses servlet-oriented `WEB-INF` locations and can separate container-provided dependencies. You normally let the plugin create these directories, but inspecting them is powerful evidence when a class or dependency is missing from the delivered artifact.

**Purpose:**

Teach the learner to use BOOT-INF and WEB-INF as packaging diagnostics rather than hand-authored structure.

## Why Is Spring Boot Loader Needed?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:07`

**Visual:**

Keep the nested dependency JARs visible, then show the ordinary JVM classpath refusing to treat them as directly addressable classpath entries.

**Script:**

Keeping JARs nested solves distribution, but it creates a launch problem the ordinary `java -jar` model does not solve by itself.

**Purpose:**

Connect the archive layout directly to the need for Spring Boot Loader.

### Scene 3 — Loader builds the effective classpath before Spring starts

**Time:** `01:07–01:51`

**Visual:**

Animate `java -jar` → manifest → Boot loader → nested libraries/classpath → `Start-Class` → `SpringApplication`. Draw a bold checkpoint before `SpringApplication`. Add one exceptional dependency marked `requiresUnpack` moving from the nested archive to a temporary filesystem location before loading.

**Script:**

Spring Boot Loader understands the executable archive layout, constructs the effective classpath from packaged locations, and invokes the configured application main class. This happens before the Spring container exists. Most dependencies run directly while nested, but a small class of libraries requires real filesystem access; Boot packaging can mark those libraries with `requiresUnpack` so the loader expands them to a temporary location at runtime. Treat that as an exceptional compatibility mechanism, not a blanket performance optimization. If the loader cannot locate a dependency or reach `Start-Class`, investigate the manifest and archive layout before Spring bean creation.

**Purpose:**

Establish Loader as the pre-runtime launch boundary and include the supported `requiresUnpack` escape hatch for exceptional nested-library compatibility.

## What Do Classpath and Layer Indexes Describe?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:51–01:58`

**Visual:**

Reveal `classpath.idx` and `layers.idx` beside the archive tree without changing any packaged files.

**Script:**

Beyond classes and libraries, the archive can carry metadata that describes how packaged content should be interpreted.

**Purpose:**

Introduce index files as metadata over an already-built artifact.

### Scene 4 — Indexes describe ordering and grouping

**Time:** `01:58–02:28`

**Visual:**

Show `classpath.idx` mapping to nested classpath order and `layers.idx` mapping files into logical layers. Keep Gradle/Maven dependency declarations outside both diagrams.

**Script:**

A classpath index can record an explicit order for nested classpath entries. A layers index records which packaged files belong to logical extraction or image layers. Neither index replaces the build's dependency graph; both describe the archive that has already been produced. Treat them as generated build output and trace unexpected entries back to packaging configuration instead of editing the indexes by hand.

**Purpose:**

Differentiate launch-order metadata from layer-grouping metadata and from source dependency declarations.

## When Does Extracting an Executable Archive Matter?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:28–02:34`

**Visual:**

Show the single executable JAR expanding into an application JAR plus a separate `lib` directory.

**Script:**

The nested single-file layout is convenient, but some production environments prefer an extracted delivery form.

**Purpose:**

Connect executable archive structure to Boot's supported extraction workflow.

### Scene 5 — Extraction changes layout, not application semantics

**Time:** `02:34–03:00`

**Visual:**

Show the command `java -Djarmode=tools -jar app.jar extract`, then the extracted layout with external libraries and the application JAR. Finish by launching the extracted application and reconnecting to the same Spring runtime diagram.

**Script:**

Spring Boot 3.3 supports extracting an executable application with the tools jar mode. The efficient layout can place libraries outside the application JAR, which may reduce nested-archive startup overhead or fit platforms that cache files separately. What changes is the delivery layout. Once the process reaches the application main class, the Spring runtime model is the same.

**Purpose:**

Show extraction as a supported packaging choice without implying a different Spring application runtime.
