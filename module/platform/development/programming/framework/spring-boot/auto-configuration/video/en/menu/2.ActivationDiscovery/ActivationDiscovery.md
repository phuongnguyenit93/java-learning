---
video:
  url: ""
---

# Activation and Candidate Discovery

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

## @SpringBootApplication and @EnableAutoConfiguration

<!-- VIDEO_SECTION -->

### Scene 1 — One annotation, two different discovery concerns

**Time:** `00:00–00:55`

**Visual:**

Open a small class annotated with `@SpringBootApplication`. Expand it visually into two branches: application component scanning and `@EnableAutoConfiguration`. The component-scan branch searches `com.example.myapp`; the auto-configuration branch points to dependency JARs outside that package.

**Script:**

Most Boot applications already enable auto-configuration through `@SpringBootApplication`. At the same time, that annotation also enables component scanning for application code. Those two paths are easy to blur together, but they solve different problems. Component scanning finds application components. Auto-configuration selection considers published configuration from dependencies, even when those classes live completely outside the application's base package.

**Purpose:**

Separate application component scanning from auto-configuration activation before introducing candidate discovery details.

## Candidate Discovery Is Not Bean Creation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Freeze the dependency-JAR branch and place a large label over it: “candidate found ≠ bean created.”

**Script:**

Finding a candidate only puts it on the list for consideration. Nothing in that step guarantees a bean will appear.

**Purpose:**

Carry the activation model into the crucial distinction between candidate existence and successful condition matching.

### Scene 2 — Ask the debugging questions in order

**Time:** `01:05–01:55`

**Visual:**

Show a vertical troubleshooting funnel: discovered? → configuration conditions matched? → bean-level conditions matched? → excluded or backed off? Place a missing `AcmeClient` icon at the bottom and animate each question as a separate checkpoint.

**Script:**

When an expected bean is missing, do not jump straight to “Boot never found my auto-configuration.” First ask whether the candidate was discovered. Then ask whether its configuration-level conditions matched, whether any bean-level conditions matched, and whether an exclusion or user choice removed the default. A candidate can be perfectly discoverable and still contribute nothing.

**Purpose:**

Teach a reusable diagnostic sequence that prevents discovery failures from being confused with selection or back-off outcomes.

## AutoConfiguration.imports and ImportCandidates

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:05`

**Visual:**

Replace the funnel with a JAR file tree and zoom into `META-INF/spring`.

**Script:**

So where does Boot get that candidate list from in the 3.3 model? The published JAR carries an explicit discovery index.

**Purpose:**

Move from the abstract notion of candidate discovery to the concrete Boot 3.3 registration mechanism.

### Scene 3 — The imports file is an index, not an outcome

**Time:** `02:05–02:55`

**Visual:**

Show `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` with two lines: `com.acme.boot.AcmeClientAutoConfiguration` and `com.acme.boot.AcmeMetricsAutoConfiguration`. Animate `ImportCandidates` reading the names into a candidate list, then stop before the condition gate.

**Script:**

In Spring Boot 3.3, a published auto-configuration lists its candidate classes in `AutoConfiguration.imports`. Boot's import-candidate infrastructure reads those class names when auto-configuration is enabled. Think of the file as an index. It answers “which configurations are available to consider?” The conditions on those classes still decide whether they apply to this application.

**Purpose:**

Make the Boot 3.3 discovery mechanism concrete while preserving the discovery-versus-matching boundary.

## Auto-configuration Packages, Component Scanning, and Explicit Imports

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:55–03:05`

**Visual:**

Move the two candidate classes into a package tree under `com.acme.boot.autoconfigure`; fade out a large `@ComponentScan` symbol.

**Script:**

Once the library has an explicit discovery index, broad component scanning is not only unnecessary here; it can make the integration boundary harder to reason about.

**Purpose:**

Connect explicit candidate registration to the package and import discipline expected of library auto-configuration.

### Scene 4 — Keep library-owned configuration explicit

**Time:** `03:05–03:55`

**Visual:**

Show `@AutoConfiguration` with `@Import(AcmeClientConfiguration.class)`. Beside it, compare a focused package tree with a red, wide scan cone that accidentally reaches unrelated classes. Highlight that consumer application scanning remains separate and valid.

**Script:**

A library should keep its auto-configuration in a package it owns, register the candidate through `AutoConfiguration.imports`, and import supporting configuration deliberately. The rule is not “component scanning is bad everywhere.” Normal application scanning is fine. The point is that a published auto-configuration should not depend on a broad scan to find its own pieces or accidentally pull in unrelated classes.

**Purpose:**

Show why explicit imports and package ownership make an auto-configuration's boundary auditable and reusable.

## Application Base Package vs Auto-configuration Discovery

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:55–04:05`

**Visual:**

Place `com.example.myapp` on the left and `com.acme.boot.autoconfigure` on the right with no package nesting between them.

**Script:**

That explicit registration also explains why an external auto-configuration does not need to sit underneath the application's main package.

**Purpose:**

Bridge package discipline to the final boundary between application base-package conventions and external candidate discovery.

### Scene 5 — Moving the main class should not reveal a published integration

**Time:** `04:05–04:55`

**Visual:**

Animate the application's `@SpringBootApplication` class moving from `com.example` to `com.example.app`. Show the application scan cone moving with it. Keep the dependency JAR and its `AutoConfiguration.imports` candidate list fixed on the right.

**Script:**

The package of the application class matters to application-side scanning conventions. But external auto-configuration discovery comes from the dependency JAR's registration, not from living below that package. Moving the main application class can change what application components are scanned. It should not be the trick that makes a correctly published external auto-configuration suddenly visible.

**Purpose:**

Close the discovery chapter by making the application-package and dependency-discovery mechanisms visually independent.
