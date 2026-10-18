---
video:
  url: ""
---

# Condition-driven Selection

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

## Why Auto-configuration Needs Conditions

<!-- VIDEO_SECTION -->

### Scene 1 — Applicability is part of the configuration contract

**Time:** `00:00–00:55`

**Visual:**

Show one reusable `AcmeClientAutoConfiguration` facing four different applications. Overlay five gates labeled library present, feature enabled, collaborator available, application type, resource present. Different applications pass different gates.

**Script:**

Reusable configuration cannot assume that every consumer has the same classpath, properties, beans, resources, or application type. Conditions turn those facts into an applicability contract. If the required technology is missing or the feature is disabled, the safe outcome is not to force configuration into the context. Conditions define when the default is valid; they are not merely a startup optimization.

**Purpose:**

Frame conditions as correctness rules for reusable configuration before examining individual condition families.

## Classpath Conditions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Zoom into the “library present” gate and replace it with `@ConditionalOnClass(AcmeClient.class)`.

**Script:**

The first input is the classpath: does the technology this integration needs even exist in the application?

**Purpose:**

Move from the general applicability model to the most common optional-technology condition.

### Scene 2 — Guard configuration with classpath facts

**Time:** `01:05–01:55`

**Visual:**

Show an `@AutoConfiguration` snippet guarded by `@ConditionalOnClass(AcmeClient.class)`. Split the screen into “class present” and “class absent.” On the absent side, stop the candidate before any Acme bean definition is registered.

**Script:**

`ConditionalOnClass` makes a classpath fact part of the decision. If `AcmeClient` is present, the candidate can continue to the next checks. If the class is absent, the Acme integration should simply not apply. At configuration-class level, Boot can inspect this annotation metadata without eagerly loading every optional type, which is why this is a useful guard for optional integrations.

**Purpose:**

Show how class conditions protect optional integrations and introduce the metadata-versus-class-loading distinction used later.

## Bean Conditions and Evaluation Timing

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:05`

**Visual:**

Shift from the classpath split to an `ApplicationContext` containing several bean-definition cards, some labeled “user configuration.”

**Script:**

Classpath tells us what technology is available. Bean conditions ask a different question: what decisions are already visible inside this context?

**Purpose:**

Bridge external availability into context-state conditions and prepare the learner for evaluation timing.

### Scene 3 — Missing-bean decisions depend on what is visible

**Time:** `02:05–02:55`

**Visual:**

Show a bean method guarded by `@ConditionalOnMissingBean`. Animate user-defined bean definitions entering the context first, followed by the auto-configuration check. Highlight the bean method's declared return type as the inferred condition target.

**Script:**

Bean conditions reason about definitions known to the context at evaluation time. Boot applies auto-configuration after user-defined bean definitions, which makes missing-bean conditions useful for back-off. The target type matters too. On a bean method, a condition can infer its target from the declared return type, so an overly broad return type can hide type information that another condition expected to see.

**Purpose:**

Explain why bean-condition results depend on definition visibility, processing time, and type information rather than on a vague idea of “bean existence.”

### Scene 4 — The `ConditionalOnExpression` early-initialization trap

**Time:** `02:55–03:45`

**Visual:**

Show a SpEL expression referencing `@acmeProperties`. Animate that bean being pulled forward on a refresh timeline before a “normal post-processing / configuration-properties binding” stage. Add a warning icon over an incompletely bound state, then replace the expression with explicit property/class/bean condition icons.

**Script:**

There is one timing trap worth making visible. If `ConditionalOnExpression` references a bean, that bean can be initialized very early during context refresh. It is then not eligible for normal post-processing such as configuration-properties binding, so the state used by the expression can be incomplete. Prefer the dedicated classpath, property, or bean condition contracts when they express the decision directly.

**Purpose:**

Preserve the Boot 3.3 caveat that bean references inside `ConditionalOnExpression` can bypass normal post-processing and produce misleading condition inputs.

## Property Conditions and Configuration Inputs

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:55`

**Visual:**

Move from bean-definition cards to an `Environment` box containing `acme.client.enabled`.

**Script:**

Some decisions do not depend on a bean at all. They come directly from configuration values already available in the environment.

**Purpose:**

Move from context bean state to externalized configuration as another selection input without changing module ownership.

### Scene 5 — Property values select, this module does not teach binding

**Time:** `03:55–04:45`

**Visual:**

Show the `@ConditionalOnProperty(prefix="acme.client", name="enabled", havingValue="true", matchIfMissing=true)` snippet. Animate three inputs: property missing → matches, `true` → matches, `false` → does not match. Put property-source precedence and binding behind a boundary labeled “Externalized Configuration module.”

**Script:**

`ConditionalOnProperty` lets environment values participate in selection. In this example, a missing property still enables the feature, `true` enables it, and `false` disables it. The important boundary is that this module explains how the resulting value affects the auto-configuration decision. How Boot loads, orders, binds, and validates configuration values belongs to Externalized Configuration.

**Purpose:**

Demonstrate property-driven selection while preserving the repository's ownership boundary for property-source and binding semantics.

## Resource and Web-application Conditions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:45–04:55`

**Visual:**

Add two more input icons beside the Environment box: a resource file and a context badge that cycles through non-web, Servlet, and Reactive.

**Script:**

Classpath, beans, and properties are common inputs, but Boot can also specialize configuration around resources and the kind of application context being created.

**Purpose:**

Extend the condition model to resource and application-type facts without drifting into MVC or WebFlux mechanics.

### Scene 6 — Keep core, Servlet, and Reactive branches independent

**Time:** `04:55–05:45`

**Visual:**

Draw one core Acme configuration with two optional branches: Servlet and Reactive. Place `@ConditionalOnWebApplication` badges on the branches and a resource gate beside another optional feature. Highlight that the core branch remains available in a non-web context.

**Script:**

`ConditionalOnResource` can require a resource, while web-application conditions let configuration specialize for a web or non-web context. That means a library can keep its core client configuration independent and activate Servlet- or Reactive-specific pieces only when the matching context exists. This module owns that selection decision; the actual MVC or WebFlux runtime behavior belongs elsewhere.

**Purpose:**

Show how application-type conditions support clean decomposition while respecting module boundaries.

## Optional Classes, Annotation Metadata, and JVM Linkage Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:45–05:55`

**Visual:**

Return to the earlier `@ConditionalOnClass` guard, then zoom past the annotation into a bean method signature that mentions `OptionalLibraryAdapter`.

**Script:**

A classpath guard does not make every optional-type reference safe. The JVM may resolve a configuration signature before a method-level condition can protect it.

**Purpose:**

Connect safe annotation-metadata inspection to the separate class-linkage risk created by optional types in configuration signatures.

### Scene 7 — Isolate optional signatures behind a class-level guard

**Time:** `05:55–06:45`

**Visual:**

First show a bean method returning `OptionalLibraryAdapter` directly in the top-level configuration and flash a “linkage may occur early” warning. Then move that method into a nested `@Configuration(proxyBeanMethods=false)` guarded by `@ConditionalOnClass(OptionalLibrary.class)`.

**Script:**

If a bean method signature itself mentions a type from an optional dependency, loading the containing configuration class may force the JVM to resolve that type before a method-level condition protects it. The safer pattern is to isolate those signatures inside a nested or separate configuration class protected by a class-level `ConditionalOnClass`. That keeps the optional branch behind a class-loading boundary.

**Purpose:**

Demonstrate why condition placement and class structure must cooperate to avoid optional-dependency linkage failures.

### Scene 8 — Prove the boundary with `FilteredClassLoader`

**Time:** `06:45–07:25`

**Visual:**

Show an `ApplicationContextRunner` chain adding `new FilteredClassLoader(OptionalLibrary.class)`. On the right, display two expected outcomes: optional branch absent, context still starts. Cross out `NoClassDefFoundError` as the failure the test is designed to prevent.

**Script:**

This is exactly the kind of behavior a focused context test can prove. Run the auto-configuration with a `FilteredClassLoader` that hides the optional library. The expected evidence is not a special HTTP response. It is that the related configuration disappears cleanly and the context still starts, instead of failing with a linkage error.

**Purpose:**

Turn the optional-class design rule into deterministic evidence using the testing mechanism that matches the lifecycle being taught.
