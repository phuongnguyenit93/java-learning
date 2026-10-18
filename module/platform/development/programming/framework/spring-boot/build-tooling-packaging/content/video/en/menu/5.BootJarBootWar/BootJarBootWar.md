---
video:
  url: ""
---

# `bootJar`, `bootWar`, and Repackaging

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

## How Do `bootJar`, `bootWar`, and Maven `repackage` Produce Boot Archives?

<!-- VIDEO_SECTION -->

### Scene 1 — Turn normal build inputs into an executable Boot archive

**Time:** `00:00–00:47`

**Visual:**

Show application classes and runtime dependencies entering three alternative operations: Gradle `bootJar`, Gradle `bootWar`, and Maven `package → repackage`. Label BootJar/BootWar as specialized Jar/War tasks. On Maven, show the executable artifact replacing the main artifact while the original is renamed `.original`, with a classifier branch that keeps both publishable artifacts.

**Script:**

Gradle and Maven still compile the application and resolve its runtime inputs. Boot's packaging step takes those inputs and gives them an executable layout. Gradle's `BootJar` and `BootWar` are specialized Jar and War tasks, so ordinary archive configuration still applies alongside Boot features. Maven's `repackage` goal transforms the archive produced by the normal package lifecycle; by default the non-executable original is renamed with `.original`, while a classifier can keep the original and attach the executable form separately. Either way, the resulting Boot archive is structured for Spring Boot Loader rather than being only a collection of compiled classes.

**Purpose:**

Explain where Boot executable packaging begins and how Gradle task specialization and Maven repackaging affect the artifacts that remain afterward.

## How Is a Boot Executable Archive Different from a Plain Library Artifact?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:47–00:55`

**Visual:**

Split the produced files into `plain.jar` and `application executable.jar`, then show one entering another application's classpath and the other being launched with `java -jar`.

**Script:**

Once two JAR files can come from the same project, their intended consumer becomes more important than the file extension.

**Purpose:**

Move from how archives are produced to why executable and library artifacts must remain distinct.

### Scene 2 — Library classpath unit versus application delivery unit

**Time:** `00:55–01:38`

**Visual:**

Show a plain JAR containing reusable classes on the left. On the right, show a Boot executable archive containing application classes, nested runtime JARs, loader classes, and manifest metadata. Highlight Gradle's conventional `plain` classifier and a caution badge: `keep the ordinary jar when native-image tooling needs it`.

**Script:**

A normal library JAR is designed to sit on another application's classpath. A Boot executable archive is an application delivery unit: it carries the application, nested runtime dependencies, and launcher metadata needed for `java -jar`. Gradle can produce both outputs and conventionally gives the ordinary JAR a `plain` classifier. The plain task can be disabled when the project truly has no consumer for it, but Spring Boot explicitly cautions against disabling it when native-image tooling needs the ordinary JAR. Publishing rules should still prevent a library consumer from accidentally selecting the executable artifact.

**Purpose:**

Clarify the semantic difference between reusable/plain output and executable packaging, including when the plain artifact still matters to downstream tooling.

## When Should You Package an Executable JAR or WAR?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:38–01:44`

**Visual:**

Replace the artifact comparison with two deployment targets: standalone JVM process and external servlet container.

**Script:**

The executable format should follow the environment that will actually launch the application.

**Purpose:**

Connect archive type to deployment contract instead of perceived maturity.

### Scene 3 — JAR and WAR are different delivery contracts

**Time:** `01:44–02:16`

**Visual:**

Show executable JAR → embedded server/JVM process. Show WAR → external servlet container, with `WEB-INF/lib-provided` highlighted for container-owned dependencies and a `providedRuntime` label beside the Gradle configuration.

**Script:**

An executable JAR is the usual choice when the application owns its Boot-managed runtime and starts as a standalone JVM process. A WAR is appropriate when an external servlet container is part of the delivery contract. For a WAR that can also run executably, container-provided libraries must remain separate from ordinary packaged libraries, which is why Boot's layout includes `WEB-INF/lib-provided` and Gradle commonly models those dependencies with `providedRuntime`.

**Purpose:**

Show that JAR-versus-WAR choice follows runtime ownership and dependency responsibility.

## How Does the Packaged Archive Identify the Application Entry Point?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:16–02:24`

**Visual:**

Open the archive manifest and place two highlighted rows: `Main-Class` and `Start-Class`.

**Script:**

An executable archive also needs to answer one final question: which code runs first when the JVM receives `java -jar`?

**Purpose:**

Move from archive layout to the launcher metadata that makes the package executable.

### Scene 4 — Loader entry point and application entry point

**Time:** `02:24–02:54`

**Visual:**

Animate `java -jar` reading `Main-Class → Spring Boot Loader`, then `Start-Class → application main`. Show an “ambiguous main classes” warning and an explicit main-class configuration resolving it.

**Script:**

Boot separates the launcher entry point from the application's own main class. The manifest's `Main-Class` enters through Spring Boot Loader, while `Start-Class` identifies the application class that the loader eventually invokes. Automatic discovery is convenient when there is one clear candidate; if several main classes exist, configure the application entry point explicitly rather than changing the manifest in a way that bypasses the loader.

**Purpose:**

Explain the two-stage entry-point model and why Boot plugin configuration should own main-class selection.
