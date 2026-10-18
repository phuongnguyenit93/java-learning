---
video:
  url: ""
---

# Dependency Management and the Spring Boot BOM

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

## What Is `spring-boot-dependencies`?

<!-- VIDEO_SECTION -->

### Scene 1 — A compatibility baseline, not a dependency bundle

**Time:** `00:00–00:26`

**Visual:**

Show a BOM card named `spring-boot-dependencies` containing version rows for Spring and third-party libraries. Draw dotted lines from only the dependencies declared by the application to matching managed versions.

**Script:**

`spring-boot-dependencies` is Spring Boot's curated bill of materials. It supplies a tested version baseline for many libraries, so application builds can omit a large number of individual version declarations. The BOM does not add every managed library to the classpath; it answers which version should be selected when the build actually declares or receives that dependency.

**Purpose:**

Separate dependency version management from dependency inclusion.

## How Do the Maven Parent and BOM Import Differ?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:26–00:32`

**Visual:**

Fork the BOM card into two Maven project diagrams: one inherits `spring-boot-starter-parent`; the other keeps a custom parent and imports only the BOM.

**Script:**

Maven can consume this baseline through two paths, but they carry different amounts of convention.

**Purpose:**

Move from the BOM concept to Maven's parent-versus-import decision.

### Scene 2 — Parent convention versus BOM-only management

**Time:** `00:32–00:56`

**Visual:**

Compare two checklists. Parent: dependency management, plugin configuration, useful defaults. BOM import: managed dependency versions while preserving another parent.

**Script:**

Using `spring-boot-starter-parent` gives a Maven project Boot's dependency management plus useful build defaults and plugin configuration. Importing `spring-boot-dependencies` in `dependencyManagement` is narrower: it brings the version baseline while allowing a different parent. These are not two spellings of the same setup; one adopts more Boot convention, the other adopts mainly dependency management.

**Purpose:**

Clarify what a Maven project gains and gives up with each supported dependency-management path.

## How Does Gradle Consume Boot-Managed Dependency Versions?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:56–01:02`

**Visual:**

Slide the Maven comparison off screen and reveal two Gradle paths: `io.spring.dependency-management` and native `platform(...)` / `enforcedPlatform(...)`.

**Script:**

Gradle reaches the same compatibility baseline through its own dependency model rather than Maven inheritance.

**Purpose:**

Connect the shared Boot BOM to Gradle-specific integration choices.

### Scene 3 — Two Gradle paths to the same baseline

**Time:** `01:02–01:48`

**Visual:**

Show the Boot plugin automatically importing its BOM when `io.spring.dependency-management` is applied. Beside it, show native `platform(...)` / `enforcedPlatform(...)` consuming the same BOM. Highlight property-based customization on the plugin path, Gradle-native constraints on the platform path, and `platform` recommendations versus `enforcedPlatform` requirements.

**Script:**

With Gradle, applying `io.spring.dependency-management` alongside the Boot plugin causes Boot's BOM to be imported automatically, and that path supports property-based customization of managed versions. The alternative is Gradle's native BOM support with `platform` or `enforcedPlatform`; it stays closer to Gradle's model and is generally the faster option. A normal platform contributes version recommendations that other constraints can influence, while an enforced platform turns the BOM versions into requirements for the configurations that consume it. In every case, Boot supplies the compatibility baseline, Gradle resolves the graph, and the application still declares the dependencies it actually uses.

**Purpose:**

Teach the stable dependency-management concept without turning the lesson into a full Gradle resolution course.

## What Responsibility Do You Take On When Overriding a Managed Version?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:48–01:58`

**Visual:**

Highlight one managed version row and animate a project override replacing it with a newer version; add a warning icon beside the compatibility baseline.

**Script:**

Managed does not mean locked forever. But the moment the project overrides the baseline, part of the compatibility decision moves back to the project.

**Purpose:**

Turn version override from a syntax detail into an explicit ownership change.

### Scene 4 — Override with evidence

**Time:** `01:58–02:38`

**Visual:**

Split the override card into two mechanics: dependency-management plugin → BOM version property; native Gradle BOM → Gradle constraint/resolution rule. Add a checklist for reason, related libraries, compatibility, tests, and a final `remove when Boot baseline catches up` condition.

**Script:**

An override can be justified by a security fix or required feature, but both the verification and the mechanism now belong to the project. With the dependency-management plugin, Boot's managed version properties are the natural customization point. With Gradle's native BOM support, those Maven-style properties do not control the platform; use Gradle constraints or resolution mechanisms instead. Check related libraries, Boot integration assumptions, and focused tests, then record why the deviation exists and the condition for removing it when a compatible Boot baseline catches up.

**Purpose:**

Make managed-version overrides a conscious compatibility decision with the correct mechanism, verification evidence, and an explicit removal condition.
