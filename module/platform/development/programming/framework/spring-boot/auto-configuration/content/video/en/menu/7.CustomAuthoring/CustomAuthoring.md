---
video:
  url: ""
---

# Authoring Custom Auto-configuration

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

## @AutoConfiguration and the Authoring Contract

<!-- VIDEO_SECTION -->

### Scene 1 — A narrow candidate with an explicit contract

**Time:** `00:00–00:55`

**Visual:**

Show a compact class containing `@AutoConfiguration`, `@ConditionalOnClass(AcmeClient.class)`, and `@EnableConfigurationProperties(AcmeProperties.class)`. Highlight `proxyBeanMethods=false` as fixed behavior of `@AutoConfiguration`, then frame the class with a boundary labeled “owns one integration.”

**Script:**

When you author auto-configuration, start small. `@AutoConfiguration` marks this class as a Boot auto-configuration candidate, while the surrounding conditions describe when the integration is valid. It is still Spring configuration, but it participates in Boot's auto-configuration pipeline and uses `proxyBeanMethods=false`. A good class owns one focused integration and contributes only the definitions that integration is responsible for.

**Purpose:**

Establish the authoring contract for a focused Boot 3.3 auto-configuration before discussing registration and supporting pieces.

## Registration, Package Boundaries, and Explicit Imports

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Pull the auto-configuration class into a JAR diagram and point from it to `AutoConfiguration.imports` and a library-owned package tree.

**Script:**

The annotation identifies the class, but it does not publish the class by itself. A consumer still needs a stable, explicit way for Boot to discover it.

**Purpose:**

Bridge the class-level contract to the packaging and registration rules that make the candidate discoverable.

### Scene 2 — Publish explicitly, compose explicitly

**Time:** `01:05–02:00`

**Visual:**

Show `com.acme.boot.autoconfigure` containing `AcmeClientAutoConfiguration`, `AcmeMetricsConfiguration`, and `AcmeProperties`. Beside it, show `AutoConfiguration.imports` containing the auto-configuration class name. Inside the class, highlight `@Import(AcmeMetricsConfiguration.class)`. Cross out a broad `@ComponentScan` over the library.

**Script:**

Put the integration under a package the library owns, list the auto-configuration in `AutoConfiguration.imports`, and import supporting configuration deliberately. That keeps discovery independent from the consumer application's scan root. It also makes the integration auditable: a reader can see which configuration belongs to the auto-configuration instead of discovering hidden components through a broad scan.

**Purpose:**

Show the three structural rules that keep published auto-configuration reusable: owned package, explicit candidate registration, and explicit supporting imports.

## Integrating @ConfigurationProperties

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:10`

**Visual:**

Move from the package tree to an `AcmeProperties` object flowing into an `AcmeClient` bean method.

**Script:**

Once the integration is discoverable, it needs a stable way to consume configuration values without taking ownership of how Boot loads those values.

**Purpose:**

Connect authoring structure to the configuration contract consumed by the auto-configuration.

### Scene 3 — Consume a structured configuration contract

**Time:** `02:10–03:00`

**Visual:**

Show `@ConfigurationProperties("acme.client")` with `URI endpoint` and a default `Duration timeout`. Animate `@EnableConfigurationProperties(AcmeProperties.class)` enabling the type and a bean method receiving the bound object. Keep property-source precedence, relaxed binding, and validation in a faded box labeled “Externalized Configuration.”

**Script:**

`@ConfigurationProperties` gives the integration a structured public configuration contract. The auto-configuration can enable that properties type and inject it into the bean methods that build or customize infrastructure. Conditions may also use selected properties to decide whether a feature participates. This module stops at that consumption boundary; property loading, precedence, relaxed binding, and validation remain owned by Externalized Configuration.

**Purpose:**

Demonstrate how custom auto-configuration consumes typed configuration while preserving the cross-module ownership boundary.

## Designing Optional Dependencies

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:00–03:10`

**Visual:**

Split the integration diagram into an autoconfigure artifact and a starter artifact, then place optional client and metrics libraries beside the autoconfigure side.

**Script:**

A reusable auto-configuration should not force every optional technology onto every consumer simply because it knows how to configure that technology.

**Purpose:**

Move from configuration values to dependency design and optional capability boundaries.

### Scene 4 — Optional dependency and optional code must agree

**Time:** `03:10–04:05`

**Visual:**

Show `acme-spring-boot` depending on Boot APIs plus optional Acme client and metrics integrations. Show `acme-spring-boot-starter` selecting the common dependency set. Then overlay a class-level `@ConditionalOnClass` and an isolated optional configuration branch.

**Script:**

The autoconfigure artifact is most reusable when dependencies for optional features remain optional. The starter can then choose the typical dependency set for most consumers, while advanced consumers can assemble a different set. But dependency metadata alone is not enough. The configuration code must also isolate optional types so a missing library does not cause early class linkage before the class condition can say “not applicable.”

**Purpose:**

Tie dependency optionality to the condition and class-loading structure required for safe reuse.

## Auto-configuration Processor and Condition Metadata

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:05–04:15`

**Visual:**

Shift from the runtime condition tree to a build-time arrow labeled “annotation processor.”

**Script:**

The conditions define behavior. Boot can also generate metadata that helps it reject some impossible candidates earlier, but that metadata must never become a second source of behavior.

**Purpose:**

Separate semantic conditions from the optional startup optimization produced at build time.

### Scene 5 — Metadata can optimize selection, not redefine it

**Time:** `04:15–05:05`

**Visual:**

Animate source annotations → auto-configuration annotation processor → `META-INF/spring-autoconfigure-metadata.properties` → early candidate filtering. Under the pipeline, keep a bold line: “runtime condition semantics remain authoritative.”

**Script:**

Boot's auto-configuration annotation processor can generate `spring-autoconfigure-metadata.properties`. That metadata lets Boot filter some clearly nonmatching candidates before doing more work. Treat it as an optimization layer. The `Conditional` annotations still define when the configuration is valid. If the integration is only correct when generated metadata happens to exist, the design is already wrong.

**Purpose:**

Prevent generated condition metadata from being mistaken for a separate or authoritative condition system.

## Stable Class Identities for Ordering and Exclusion

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:05–05:15`

**Visual:**

Put the auto-configuration class name in the center and draw incoming references from “before/after,” `exclude`, and `excludeName`.

**Script:**

An auto-configuration class is rarely instantiated by application code, but other configuration can still refer to its identity directly. That makes naming stability a real compatibility concern.

**Purpose:**

Move from internal authoring mechanics to the public compatibility surface created by ordering and exclusion references.

### Scene 6 — In Boot 3.3, class identity changes need compatibility planning

**Time:** `05:15–06:10`

**Visual:**

Show `com.acme.boot.OldAutoConfiguration` being renamed to `NewAutoConfiguration`. Break three arrows labeled before/after, exclude, excludeName. Add a Boot 3.3 badge and a callout: “no general `AutoConfiguration.replacements` mapping in this baseline.” End with “choose stable package + class names.”

**Script:**

Ordering relationships and application exclusions can reference an auto-configuration by type or by class name. In this repository's Spring Boot 3.3 baseline, there is no general `AutoConfiguration.replacements` mechanism that transparently remaps an old identity to a new one. Renaming or moving a published auto-configuration therefore needs compatibility planning. Choose stable package and class names, and if a breaking move is unavoidable, review every ordering, exclusion, documentation, and consumer reference that may depend on that identity.

**Purpose:**

Preserve the Boot 3.3 version boundary and show why auto-configuration class identity behaves like part of the public integration contract.
