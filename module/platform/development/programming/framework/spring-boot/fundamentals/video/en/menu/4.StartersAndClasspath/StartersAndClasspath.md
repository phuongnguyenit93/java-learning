---
video:
  url: ""
---

# Starters, Managed Dependencies, and the Classpath

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

## What Is a Spring Boot Starter and Why Use One?

<!-- VIDEO_SECTION -->

### Scene 1 — A starter expresses dependency intent

**Time:** `00:00–00:55`

**Visual:**

Open a small Gradle `dependencies` block containing only `implementation 'org.springframework.boot:spring-boot-starter-web'`. Expand that one line into a dependency tree showing Spring web libraries plus supporting third-party libraries. Keep a note on screen: “starter shapes classpath; it does not run the app”.

**Script:**

“A Spring Boot starter is a dependency descriptor for a common application capability. Instead of discovering every Spring and third-party library one by one, you declare a starter that represents a supported starting point. For a web use case, the web starter brings a conventional web dependency set onto the classpath. The starter itself does not start a server or register application beans. Its primary job is to express dependency intent and shape the classpath conveniently.”

**Purpose:**

Define a starter as dependency convenience and prevent the common mistake of treating it as a runtime mechanism.

## What Problem Do Managed Dependency Versions Solve?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Keep the expanded dependency tree and overlay several conflicting version badges, then replace them with one “Spring Boot release” badge controlling the set.

**Script:**

“Collecting useful libraries is only half the dependency problem. Those libraries also need versions that are intended to work together.”

**Purpose:**

Move from dependency selection to coordinated dependency-version management.

### Scene 2 — A coordinated baseline reduces version guesswork

**Time:** `01:05–02:00`

**Visual:**

Show a simple flow: `Boot version → curated dependency set → Spring libraries + selected third-party libraries`. Beside it, show a dependency declaration without an explicit version, then a separate “override” path with a warning label “you own more compatibility risk”.

**Script:**

“A real application contains many libraries, and picking every version independently can produce incompatible combinations. Each Boot release publishes a curated dependency set. When the build uses Boot’s dependency management, many common libraries can be declared without repeating a version because the Boot line supplies the coordinated baseline. You can still override a managed version, but that is an intentional exception: part of the compatibility responsibility moves back to the application team.”

**Purpose:**

Explain managed dependencies as compatibility coordination rather than a rule that versions can never be overridden.

## How Can the Classpath Change Boot Behavior?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:10`

**Visual:**

Collapse the dependency tree into a single “classpath” strip and animate one web library appearing on it.

**Script:**

“Those dependency choices matter beyond compilation because Boot also uses the classpath as evidence about which technologies are available.”

**Purpose:**

Carry dependency choices into their runtime effect while preserving responsibility boundaries.

### Scene 3 — Classpath is a signal, not the whole decision engine

**Time:** `02:10–03:10`

**Visual:**

Start with “non-web classpath” feeding `SpringApplication` and a plain context. Add the usual Servlet web stack. Inside a separate `SpringApplication` box, highlight “determine WebApplicationType → create corresponding context”. Then show a second box labeled “auto-configuration” contributing embedded Servlet-server infrastructure when conditions match.

**Script:**

“Suppose the application starts with no web stack. Add the usual Servlet web dependencies, and the available capabilities change. By default, SpringApplication uses classpath evidence to determine the WebApplicationType and creates the corresponding application context. Auto-configuration has a different responsibility: during context construction it can contribute and configure matching infrastructure, such as an embedded Servlet web server, when its conditions are satisfied. Classpath presence is important, but properties, existing beans, and other conditions can also affect auto-configuration.”

**Purpose:**

Show exactly how classpath affects application shape while keeping SpringApplication context selection separate from auto-configuration infrastructure contribution.

## Why Is a Starter Not the Same as Auto-Configuration?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:10–03:20`

**Visual:**

Split the screen into “build/dependency side” and “context-configuration side”.

**Script:**

“Because starters and auto-configuration often appear together, the next distinction is worth making explicit.”

**Purpose:**

Turn the previous example into a clean mechanism comparison.

### Scene 4 — Dependency descriptor versus configuration mechanism

**Time:** `03:20–04:10`

**Visual:**

Show a two-column table. Starter: dependency descriptor, curated libraries, classpath. Auto-configuration: Boot configuration classes and conditions, contributes configuration when conditions match. Under the table, animate `spring-boot-starter-web → classpath`, then separately `web auto-configuration → matching beans/integrations`.

**Script:**

“A starter and an auto-configuration solve different problems. The starter is build-time dependency convenience: it puts a supported set of libraries on the classpath. Auto-configuration is an application-context configuration mechanism: it contributes configuration when its conditions match. Declaring spring-boot-starter-web does not itself register server beans. It gives the application a conventional web classpath that Boot mechanisms can observe.”

**Purpose:**

Give learners a diagnostic distinction between “is the dependency available?” and “why was configuration contributed or skipped?”.

## When Should You Use a Starter, an Individual Dependency, or an Override?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:10–04:20`

**Visual:**

Replace the comparison table with a three-branch decision diagram.

**Script:**

“Once the roles are separate, dependency choice becomes a simple decision instead of a collection of special cases.”

**Purpose:**

Move from mechanism understanding to a practical dependency-selection rule.

### Scene 5 — Choose the narrowest sensible dependency strategy

**Time:** `04:20–05:10`

**Visual:**

Reveal three branches: “common Boot use case → starter”; “narrow library need → individual dependency”; “exception to managed baseline → explicit version override + verification”. Add a footer pointing BOM/plugin/task details to `build-tooling-packaging`.

**Script:**

“Use a starter when it accurately represents a common capability you need and you want Boot’s conventional dependency set. Use an individual dependency when the need is narrow and you do not want the wider starter. Override a managed version only for a concrete compatibility, security, or feature requirement, and verify the resulting combination. The exact BOM import, Gradle or Maven plugin mechanics, and packaging tasks belong to build-tooling-packaging.”

**Purpose:**

Close with a practical dependency-choice model and a clear handoff to build tooling details.
