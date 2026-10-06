---
video:
  url: ""
---

# Ordering and Composition

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

## Configuration Definition Order vs Bean Creation Order

<!-- VIDEO_SECTION -->

### Scene 1 — Two timelines, two responsibilities

**Time:** `00:00–00:55`

**Visual:**

Show two horizontal timelines. Top: “configuration-definition processing” with `AutoConfiguration A` before `B`. Bottom: “bean creation” with `Bean X → depends on → Bean Y`, where Y is created first because of the dependency. Keep the timelines visually separate.

**Script:**

Auto-configuration ordering controls when configuration definitions are processed. It does not tell Spring which bean instance must be created first. If configuration B needs to reason about definitions contributed by A, configuration ordering can matter. If bean X actually depends on bean Y, express that through the container dependency. Mixing those two timelines creates fragile designs because they solve different problems.

**Purpose:**

Establish the chapter's most important boundary: configuration processing order is not bean-instantiation order.

## Before and After Relationships

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Keep the configuration timeline and add an arrow from `AcmeCoreAutoConfiguration` to `AcmeMetricsAutoConfiguration` labeled “before.”

**Script:**

When two auto-configurations really do have a definition-processing relationship, Boot lets that relationship be stated directly.

**Purpose:**

Move from the general ordering boundary to explicit dependency-oriented ordering tools.

### Scene 2 — Prefer relationships that explain why order exists

**Time:** `01:05–01:55`

**Visual:**

Show `@AutoConfiguration(after = AcmeCoreAutoConfiguration.class)` and, beside it, `@AutoConfigureAfter(name = "com.acme...AcmeCoreAutoConfiguration")`. Highlight the name-based form as avoiding a hard type reference. Animate core definitions becoming visible before metrics conditions are processed.

**Script:**

Boot supports before and after relationships through `@AutoConfiguration` attributes and the dedicated ordering annotations. Use them when one configuration genuinely needs another to have an opportunity to register definitions first. A name-based relationship is useful when you do not want the other auto-configuration class to become a hard classpath dependency. The point is to document a real dependency, not to make a list look tidy.

**Purpose:**

Show how explicit before/after relationships communicate causal ordering and how name-based references preserve optionality.

## Ordering Independent Auto-configurations

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:05`

**Visual:**

Remove the direct arrow between the two configurations and replace it with a numbered ordering scale.

**Script:**

Sometimes there is no direct A-depends-on-B relationship, but a group of auto-configurations still needs a relative position in the selection sequence.

**Purpose:**

Bridge explicit dependency ordering to the broader numeric ordering mechanism.

### Scene 3 — Numeric order stays inside the auto-configuration pipeline

**Time:** `02:05–02:50`

**Visual:**

Place three auto-configurations on an `@AutoConfigureOrder` scale. Below it, show runtime listeners, filters, callbacks, and bean creation behind a barrier labeled “not ordered by this value.”

**Script:**

`AutoConfigureOrder` gives auto-configurations a relative ordering value when a direct before-or-after relationship is not the right model. Keep its scope narrow in your mental model. That value belongs to auto-configuration processing. It does not automatically order filters, listeners, arbitrary callbacks, or bean creation. When a concrete dependency exists, a direct relationship is usually clearer than a broad numeric position.

**Purpose:**

Prevent numeric auto-configuration order from being misapplied to unrelated runtime ordering concerns.

## Composing Conditional Configuration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:**

Transform the linear ordering scale into a tree rooted at `AcmeClientAutoConfiguration` with core, metrics, and Servlet branches.

**Script:**

Ordering helps configurations cooperate, but large integrations become easier to reason about when optional concerns are also separated into focused branches.

**Purpose:**

Move from relative ordering into structural composition around applicability boundaries.

### Scene 4 — Put conditions next to the feature they protect

**Time:** `03:00–03:50`

**Visual:**

Show a top-level auto-configuration explicitly importing three focused configurations: core client, metrics guarded by a metrics class, and Servlet guarded by application type. Fade one optional branch without affecting the others. Cross out a broad component-scan cone.

**Script:**

A focused integration can keep one top-level auto-configuration and compose smaller conditional pieces underneath it. The metrics branch owns its metrics condition; the Servlet branch owns its web-context condition. If one optional feature is unavailable, that branch disappears without taking down the core configuration. Explicit imports keep that composition visible and auditable instead of hiding it behind a broad component scan.

**Purpose:**

Show how conditional decomposition reduces coupling and makes optional feature boundaries explicit.

## Isolating Optional Technology

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:**

Zoom into the metrics branch and highlight a method signature that mentions an optional metrics type.

**Script:**

Structural composition also solves a class-loading problem: optional code must be isolated far enough that missing types are never resolved too early.

**Purpose:**

Connect composition to the optional-type linkage boundary established in the condition chapter.

### Scene 5 — Isolation is both a dependency and class-loading boundary

**Time:** `04:00–05:00`

**Visual:**

Show the pattern: top-level auto-configuration → explicit import → nested or separate optional configuration → class-level `@ConditionalOnClass` → bean methods that mention optional types. Then show a `FilteredClassLoader` test hiding the optional package and an outcome panel: core bean remains, optional bean absent, context starts.

**Script:**

Marking a dependency optional is only half the design. The code that references that dependency must also sit behind a safe class-loading boundary. A nested or separate configuration protected by a class-level class condition gives those method signatures somewhere safe to live. The trade-off is a few more configuration classes. The payoff is much stronger: a missing optional library removes only its branch. A `FilteredClassLoader` context test is a clean way to prove that boundary.

**Purpose:**

Tie optional dependency design, conditional composition, and class-loading safety together with focused executable evidence.
