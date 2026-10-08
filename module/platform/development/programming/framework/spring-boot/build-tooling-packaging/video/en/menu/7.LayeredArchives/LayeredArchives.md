---
video:
  url: ""
---

# Layered JAR and WAR Packaging

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

## What Problem Do Layered Archives Solve?

<!-- VIDEO_SECTION -->

### Scene 1 — Separate content by how often it changes

**Time:** `00:00–00:24`

**Visual:**

Show one large application archive where a tiny class change invalidates the whole block. Then split the same content into stable dependencies, loader, snapshot dependencies, and application content.

**Script:**

Application classes usually change much more often than released third-party dependencies. A layered Boot archive records logical groups so downstream extraction or image tooling can keep stable content separate from volatile content. The Java dependencies are unchanged; layering adds packaging metadata that can make rebuilds and transfers more cache-friendly.

**Purpose:**

Motivate layering through change frequency and cache reuse rather than Java dependency semantics.

## What Are the Default Spring Boot Archive Layers?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:24–00:32`

**Visual:**

Turn the generic groups into four named blocks in the order used by Boot.

**Script:**

Spring Boot provides a useful default grouping so every project does not need to invent its own layer taxonomy.

**Purpose:**

Move from the purpose of layers to Boot's default layer model.

### Scene 2 — Read the four default groups

**Time:** `00:32–00:58`

**Visual:**

Stack `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, and `application`. Add examples: released external JAR, loader classes, SNAPSHOT JAR, project classes/local module dependency.

**Script:**

The default model separates released dependencies, Spring Boot Loader, snapshot dependencies, and application content. This grouping reflects expected change frequency, not business architecture. Project dependencies normally land with application content, while released external libraries can sit in the stable dependencies layer. The generated `layers.idx` is the evidence for where the current build actually placed each file.

**Purpose:**

Teach the default layer names and the reasoning behind their membership.

## Why Does Layer Order Affect Cache Reuse?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:04`

**Visual:**

Animate an application class change and keep the first three layer blocks unchanged.

**Script:**

Grouping files helps only if downstream tooling preserves those boundaries in a useful order.

**Purpose:**

Connect logical layer membership to practical cache behavior.

### Scene 3 — Stable layers survive frequent source edits

**Time:** `01:04–01:32`

**Visual:**

Show a cache timeline: dependencies and loader reused, application layer rebuilt. Then show a counterexample Dockerfile copying the entire archive as one opaque layer, invalidating everything when the JAR changes.

**Script:**

Container caches can reuse earlier unchanged layers when stable dependencies are separated from frequently changing application code. But a layered archive does not guarantee that benefit by itself. If an image recipe copies the whole archive as one opaque file, the inner grouping cannot be reused independently. Packaging metadata and the image-building strategy have to agree on how layers are consumed.

**Purpose:**

Explain why layer order and downstream consumption jointly determine cache reuse.

## When Should Layering Be Customized?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:32–01:39`

**Visual:**

Show the default four layers, then overlay an observed cache profile where one large internal library changes far less often than the application.

**Script:**

Defaults are a starting point. Customization becomes useful only when measured project behavior disagrees with the default assumptions.

**Purpose:**

Frame custom layering as an evidence-driven optimization.

### Scene 4 — Customize for real change patterns

**Time:** `01:39–02:18`

**Visual:**

Create a custom “stable internal libraries” layer and an “application” layer. Show three rules: claim content into named layers; earlier matching rules win when patterns overlap; every destination layer must appear in the complete layer order. Add a warning against unclaimed content and dozens of package-named layers.

**Script:**

Customize layering when a stable pattern justifies it, for example when large internal libraries change much less often than application code. Custom rules must assign content to meaningful layers and provide a complete order. When several patterns could claim the same content, rule order determines which layer receives it, and content still needs a valid destination represented in the final order. Avoid turning every package into a layer; that couples cache policy to source organization and creates maintenance cost without proving a cache benefit.

**Purpose:**

Give a disciplined rule for when custom layers help and the matching/order constraints that make a custom scheme complete.

## What Does Archive Layering Not Define About Container Runtime Behavior?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:18–02:27`

**Visual:**

Place `layers.idx` on the build side and a running container with network, volume, CPU, and rollout controls on the runtime side.

**Script:**

The word “layer” also appears in container discussions, so the final boundary is about what Boot archive layers do not control.

**Purpose:**

Prevent packaging-layer terminology from being confused with container-runtime behavior.

### Scene 5 — Packaging metadata stops before container operations

**Time:** `02:27–02:56`

**Visual:**

Left: archive grouping and extraction/cache. Right: container networking, mounts, limits, orchestration, registry, rollout. Draw an arrow showing archive layers may inform image construction but do not configure the runtime.

**Script:**

Boot archive layers describe how application files are grouped for packaging and extraction. They do not define container networking, volumes, process isolation, CPU or memory limits, registry behavior, or rollout strategy. If a dependency lands in the wrong Boot layer, inspect packaging rules. If the image is correct but runtime mounts or limits behave incorrectly, the artifact has already crossed into container-platform ownership.

**Purpose:**

Close with a clear troubleshooting boundary between archive layering and container runtime concerns.
