---
video:
  url: ""
---

# Running Applications with Build Tooling

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

## What Is a Boot-Aware Development Run?

<!-- VIDEO_SECTION -->

### Scene 1 — Run from the build model, before packaging

**Time:** `00:00–00:29`

**Visual:**

Show source compilation feeding `classes + runtime classpath`, then a direct arrow to a running JVM. Keep `bootJar → app.jar` visible as a separate, later path.

**Script:**

A Boot-aware development run starts the application from the build's compiled output and runtime classpath. That makes it fast for the edit-build-run loop because a final executable archive is not required for every launch. The evidence is intentionally narrow: a successful development run proves the application can start from the build model, not that the final archive layout and loader are correct.

**Purpose:**

Define what development-run success proves and what it does not prove.

## How Does Gradle `bootRun` Launch the Application?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:29–00:37`

**Visual:**

Zoom into the Gradle branch and place a `bootRun` task node between the main source set and the JVM process.

**Script:**

Gradle makes this development path concrete with a task that is still part of the normal Gradle execution model.

**Purpose:**

Move from the generic development-run idea to Gradle's BootRun behavior.

### Scene 2 — BootRun as a Java execution task

**Time:** `00:37–01:15`

**Visual:**

Show `./gradlew bootRun`, then label inputs: main source-set output, runtime classpath, discovered main class, JVM args, application args, environment. Highlight separate lanes for JVM arguments and application arguments, plus an `optimizedLaunch=true → false for diagnosis` toggle.

**Script:**

In Spring Boot 3.3, `BootRun` is a `JavaExec` subclass with Boot conventions. It uses the application's main runtime classpath, can discover the main class, and exposes normal Java process controls such as JVM arguments, system properties, environment variables, and application arguments. Boot also enables an optimized development launch by default; `optimizedLaunch` can be disabled when a diagnostic comparison needs the normal JVM launch behavior. Keep the input channels separate: an application argument is not the same thing as a JVM system property.

**Purpose:**

Show how BootRun composes normal JavaExec controls with Boot's runtime classpath, main-class discovery, and optimized development launch.

## How Does Maven `spring-boot:run` Launch the Application?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:21`

**Visual:**

Slide from the Gradle task graph to a Maven command line while keeping the same “compiled output + dependencies → JVM” diagram.

**Script:**

Maven reaches the same developer outcome through a plugin goal instead of a Gradle task.

**Purpose:**

Preserve the common launch concept while switching build-tool mechanics.

### Scene 3 — Maven's development launch path

**Time:** `01:21–01:47`

**Visual:**

Show `./mvnw spring-boot:run` and callouts for application arguments, JVM arguments, environment variables, system properties, and profiles. Keep the Maven lifecycle as a surrounding frame.

**Script:**

`spring-boot:run` assembles the project's application classpath and launches the configured Boot main class through Maven's plugin integration. Maven still resolves the dependencies and owns its lifecycle; the plugin provides the Boot-aware launch. If a failure can only appear after packaging, this goal is the wrong test because the executable archive boundary has not been exercised.

**Purpose:**

Explain Maven's run goal and keep its evidence boundary aligned with the Gradle path.

## When Should You Use a Build-Tool Run Instead of `java -jar`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:47–01:53`

**Visual:**

Put `bootRun / spring-boot:run` and `java -jar app.jar` side by side with labels “development classpath” and “packaged artifact”.

**Script:**

Both commands may start the same application, but they test different delivery boundaries.

**Purpose:**

Prepare a direct evidence comparison between development execution and packaged execution.

### Scene 4 — Choose the run path for the question

**Time:** `01:53–02:22`

**Visual:**

Show a decision card: “editing/debugging?” → build-tool run; “manifest, loader, BOOT-INF, packaged dependencies?” → build archive then `java -jar`. Add `developmentOnly` as a dependency visible only on the development side.

**Script:**

Use the build-tool run when you want rapid development feedback. Use `java -jar` when you need evidence about the artifact that will be delivered. The difference matters because development-only dependencies can participate in `bootRun` while being intentionally absent from the production archive. A release check should therefore launch the actual JAR or image, not treat an IDE or build-tool launch as packaging proof.

**Purpose:**

Teach the learner to select a launch method based on the boundary being verified.

## What Does the Run Task Configure, and What Still Belongs to `SpringApplication`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:22–02:30`

**Visual:**

Freeze the process-launch diagram exactly at the application main method and draw a boundary before `SpringApplication.run`.

**Script:**

The run task controls how the process is started. Once the application enters Spring Boot runtime, the owner changes again.

**Purpose:**

Separate launch configuration from runtime lifecycle semantics.

### Scene 5 — Process launch ends where application runtime begins

**Time:** `02:30–02:58`

**Visual:**

Left side: classpath, main class, JVM args, app args, environment. Right side after `SpringApplication.run`: context creation, events, runners, availability, failure analysis, shutdown.

**Script:**

The build plugin decides which classpath and main class the JVM receives and which launch inputs are passed to the process. After the main method calls `SpringApplication.run`, context creation, application events, runners, availability, failure analysis, and shutdown are runtime behavior. If the same failure also happens with `java -jar`, continuing to tune `bootRun` is usually looking at the wrong owner.

**Purpose:**

End with a troubleshooting boundary between build-time launch configuration and SpringApplication runtime behavior.
