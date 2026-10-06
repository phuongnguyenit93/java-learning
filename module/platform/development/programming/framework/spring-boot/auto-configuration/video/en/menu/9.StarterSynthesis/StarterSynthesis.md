---
video:
  url: ""
---

# Starter Design and Synthesis

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

## What a Starter Adds to Auto-configuration

<!-- VIDEO_SECTION -->

### Scene 1 — The starter chooses dependencies; auto-configuration chooses behavior

**Time:** `00:00–00:55`

**Visual:**

Show a developer adding one `acme-spring-boot-starter` dependency. Expand it into typical libraries plus the autoconfigure artifact. Then separate two labels: “starter → dependency opinion” and “auto-configuration → conditional behavior.”

**Script:**

A starter and an auto-configuration are related, but they do different jobs. The starter makes the dependency choice convenient. Adding one dependency should bring the typical pieces needed to use the integration. The auto-configuration contains the conditional logic that decides which defaults actually join the context. Keeping those roles separate makes the rest of starter design much easier to reason about.

**Purpose:**

Establish the final synthesis chapter's key distinction between dependency convenience and runtime conditional behavior.

## Separate Autoconfigure and Starter Modules vs a Combined Starter

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Split the starter into two artifact boxes, then merge them back into one box to show both valid layouts.

**Script:**

Those two roles often live in separate artifacts, but the split is a design choice, not a mandatory shape for every integration.

**Purpose:**

Move from conceptual roles to the artifact-layout trade-off used by starter authors.

### Scene 2 — Split when dependency choice needs room to vary

**Time:** `01:05–01:55`

**Visual:**

Compare two layouts. Layout A: `acme-spring-boot` contains auto-configuration, properties, extension APIs; `acme-spring-boot-starter` supplies the common dependency set. Layout B: one combined starter for a simple integration. Add an “optional features / multiple starter opinions” arrow favoring the split.

**Script:**

A separate autoconfigure artifact gives advanced consumers access to the conditional configuration without forcing the starter's entire dependency opinion. That becomes valuable when optional technologies or multiple common dependency sets matter. For a straightforward integration with little optionality, combining the roles into one starter can be perfectly reasonable. Choose the structure from extension and dependency needs, not from a rule that every starter must have two modules.

**Purpose:**

Explain the trade-off behind split versus combined starter layouts without turning a common convention into a rigid rule.

## Starter Naming and Package Ownership

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:05`

**Visual:**

Keep the artifact boxes and replace generic names with official-looking `spring-boot-*` names on one side and `acme-spring-boot-*` names on the other.

**Script:**

Once the artifact shape is chosen, naming should make ownership just as clear as the dependency graph.

**Purpose:**

Bridge artifact structure into the public naming and package contract for third-party integrations.

### Scene 3 — Use the namespace you actually own

**Time:** `02:05–02:50`

**Visual:**

Cross out a third-party artifact pretending to be an official `spring-boot-*` module. Highlight `acme-spring-boot` and `acme-spring-boot-starter`. Beneath them, show `com.acme.boot.autoconfigure` outside the consumer's `com.example.myapp` package tree.

**Script:**

Third-party starters should use a namespace owned by the library instead of looking like official Spring Boot modules. Package ownership follows the same principle. Auto-configuration classes belong under the library's package, not under the consumer application's package. Stable names also matter because ordering and exclusion references may point at those class identities later.

**Purpose:**

Make ownership visible in both artifact names and Java packages while connecting naming stability back to ordering and exclusion compatibility.

## Configuration-key Namespace and Metadata

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:**

Move from the Java package tree to configuration keys under `acme.client.*`.

**Script:**

Ownership should remain obvious when the integration exposes configuration to the application as well.

**Purpose:**

Carry the namespace principle from artifacts and packages into public configuration keys.

### Scene 4 — Configuration keys are part of the public contract

**Time:** `03:00–03:50`

**Visual:**

Show `acme.client.endpoint`, `acme.client.timeout`, and `acme.client.enabled`. Contrast them with crossed-out third-party keys under `spring.*`, `server.*`, or `management.*`. Then show an IDE completion popup sourced from generated configuration metadata.

**Script:**

Use a configuration-key namespace the library owns, such as `acme.client`. A third-party integration should not place its own settings inside Boot-owned namespaces like `spring`, `server`, or `management`. Document the properties well enough that generated configuration metadata can give useful IDE assistance, and inspect that metadata to make sure descriptions and types match the public contract. The binding mechanics themselves still belong to Externalized Configuration.

**Purpose:**

Show how a collision-resistant property namespace and useful metadata support a stable external starter contract without re-teaching binding.

## Dependency Opinion and Optional Features

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:**

Return to the starter dependency graph and mark several integrations as required or optional.

**Script:**

A starter is intentionally opinionated, but the opinion should describe the common path rather than forcing every optional feature onto every consumer.

**Purpose:**

Move from naming the contract to choosing the dependency set that contract should provide by default.

### Scene 5 — Keep optional capabilities optional

**Time:** `04:00–04:50`

**Visual:**

Show one starter with core Acme dependencies and optional metrics or tracing integrations left outside. Then show two alternative starters reusing the same autoconfigure artifact but choosing different common dependency sets. Keep a condition icon on each optional branch.

**Script:**

The starter should include what most users need, while avoiding unnecessary optional technologies. If an integration has several optional features, a separate autoconfigure artifact gives consumers and additional starters room to choose different dependency opinions while reusing the same conditional configuration. Conditions make optional behavior possible; they do not justify putting every optional library on every consumer's classpath.

**Purpose:**

Connect starter dependency choices to the optional-condition design without confusing “can configure” with “must depend on.”

## From Starter Dependency to Configured Application

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:50–05:00`

**Visual:**

Clear the screen and reveal an empty end-to-end pipeline with space for each concept from the module.

**Script:**

Now put every piece together from the moment the application adds a starter to the moment the resulting decision is tested and explained.

**Purpose:**

Prepare the learner for the complete synthesis of dependency, discovery, selection, back-off, diagnostics, and testing.

### Scene 6 — The full auto-configuration path

**Time:** `05:00–06:10`

**Visual:**

Progressively reveal: application adds starter → starter supplies typical dependencies → dependency JAR exposes `AutoConfiguration.imports` → Boot discovers candidates → ordering coordinates configuration processing → conditions inspect classpath/properties/beans/context → matching defaults contribute definitions → user choices cause back-off → Condition Evaluation Report explains the result → focused context tests prove the contract. Keep discovery/matching and configuration/bean-creation boundaries visibly separated.

**Script:**

The application adds a starter, which supplies the typical dependencies. One of those dependency JARs publishes auto-configuration candidates through `AutoConfiguration.imports`. Boot discovers and orders those candidates, evaluates their conditions against the current classpath, properties, bean definitions, resources, and context type, and contributes definitions from the candidates that match. Supported user choices cause defaults to back off. The Condition Evaluation Report explains why a decision happened, and focused context tests prove the same contract across controlled variants. That is the end-to-end model behind the convenience of “just add the starter.”

**Purpose:**

Synthesize every major concept in the module into one traceable lifecycle without attributing bean creation directly to the starter itself.

## Boundaries and Next Learning Steps

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:10–06:20`

**Visual:**

Shrink the end-to-end pipeline into the center and place five neighboring module cards around it: Spring Framework container, Externalized Configuration, Testing, Build Tooling and Packaging, Native Image.

**Script:**

That complete flow also tells us where this module stops. Several adjacent questions matter, but they belong to different owners in the learning path.

**Purpose:**

Turn the synthesis into a clear ownership boundary and handoff to neighboring modules.

### Scene 7 — Keep the durable mental model and hand off the rest

**Time:** `06:20–07:10`

**Visual:**

Highlight each neighboring card as the narration names it. End by returning to a five-part summary in the center: dependencies make capability available → discovery finds candidates → conditions select → back-off preserves control → diagnostics and focused tests make decisions observable.

**Script:**

If the question becomes “how does Spring create and manage these beans?”, move to the core container. If it is about property loading, ordering, binding, or validation, move to Externalized Configuration. Whole-application testing belongs to the Boot Testing module. BOMs, plugins, packaging, and images belong to Build Tooling and Packaging, while AOT and native-image constraints belong to Native Image. The model to keep from this module is simpler: dependencies make capabilities available, discovery finds candidates, conditions select what fits, back-off preserves application control, and diagnostics plus focused tests make those decisions observable.

**Purpose:**

Close the module with a concise mental model and explicit ownership boundaries so later learning builds on the right abstraction layer.
