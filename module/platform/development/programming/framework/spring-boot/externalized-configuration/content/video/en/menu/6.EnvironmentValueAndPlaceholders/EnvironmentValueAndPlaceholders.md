---
video:
  url: ""
---

# Environment, Placeholders, and @Value

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

## Reading Resolved Values Through Environment

<!-- VIDEO_SECTION -->

### Scene 1 — Read One Effective Value

**Time:** `00:00–00:45`

**Visual:**

Show file, environment, and CLI candidates fading into the background while `String region = environment.getProperty("app.region");` remains in focus and returns one value.

**Script:**

Once Boot has assembled and ordered its property sources, application code can query the `Environment` by key and receive the effective value. The caller does not need to know which source supplied it. That is useful for focused access, although related settings are usually clearer when they are grouped into a typed configuration object.

**Purpose:**

Show direct resolved-value access through `Environment` without reintroducing source-specific reads.

## How ${...} Placeholders Resolve

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–00:55`

**Visual:**

Keep `Environment` on screen and add one property whose value references another property with `${...}`.

**Script:**

Resolved values can also feed other configuration values through placeholders.

**Purpose:**

Move from direct Environment access to placeholder-based composition.

### Scene 2 — Placeholders Resolve Against Environment

**Time:** `00:55–01:40`

**Visual:**

Show `app.region=eu-west` and `app.label=${app.region}-worker`. Animate the placeholder lookup through `Environment`, then reveal `app.label=eu-west-worker`.

**Script:**

Placeholders are resolved against the same Environment view, so they can compose values regardless of which source supplied the referenced key. If a higher-precedence source changes `app.region`, the composed label follows that effective region. This is more than textual substitution inside one file.

**Purpose:**

Explain placeholder resolution as Environment lookup rather than file-local string replacement.

## Placeholder Defaults and Missing Values

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:40–01:50`

**Visual:**

Remove `app.region` and compare a placeholder with and without an explicit fallback.

**Script:**

Once placeholders depend on Environment keys, missing values need an explicit contract too.

**Purpose:**

Move from successful placeholder resolution to missing-value behavior.

### Scene 3 — Defaults Should Express Real Fallbacks

**Time:** `01:50–02:35`

**Visual:**

Compare `${app.region}` with `${app.region:local}`. Show the first remaining unresolved where the consumer requires a value, while the second resolves to `local`.

**Script:**

A placeholder can include a default after a colon. Use that only when absence has a legitimate fallback meaning. If the setting is required, silently defaulting it can hide a deployment mistake. The default belongs in the configuration contract, not in a workaround for missing configuration.

**Purpose:**

Teach placeholder defaults as intentional fallback behavior rather than a way to suppress required configuration errors.

## Focused Scalar Injection with @Value

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:35–02:45`

**Visual:**

Move the placeholder expression from a configuration file into a small Spring component field annotated with `@Value`.

**Script:**

When one component needs one focused scalar, Spring can inject that resolved value directly.

**Purpose:**

Connect placeholder resolution to `@Value` consumption.

### Scene 4 — Use `@Value` for Focused Scalars

**Time:** `02:45–03:30`

**Visual:**

Open a tiny component and highlight `@Value("${app.region}") private String region;`. Animate the effective value from `Environment` into the field.

**Script:**

`@Value` is convenient when a component needs one isolated value or a small expression. It keeps that dependency close to the consumer. As related settings grow into a reusable configuration domain, repeating string keys across many components becomes harder to refactor, validate, and document.

**Purpose:**

Position `@Value` as a focused scalar-consumption tool rather than the default for structured configuration.

## Why Canonical Kebab-Case Property Names Matter

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Keep the `@Value` placeholder and show several source-specific spellings of the same logical property around it.

**Script:**

Direct string keys make naming conventions visible, and one canonical spelling gives Boot the most predictable cross-source behavior.

**Purpose:**

Introduce canonical property naming from a concrete placeholder use case.

### Scene 5 — Keep One Canonical Public Name

**Time:** `03:40–04:25`

**Visual:**

Highlight `demo.item-price` as canonical. Around it show suitable source spellings such as `demo.itemPrice` and an environment-variable form, then dim a camel-case placeholder as less portable.

**Script:**

Canonical lower-case kebab case is the safest form for documentation, metadata, prefixes, and placeholders. Source-specific relaxed forms can still map to the same logical property where supported. Treat the kebab-case name as the public configuration contract and derive other spellings from it.

**Purpose:**

Show why canonical kebab-case naming improves predictable lookup and documentation across configuration sources.

## @Value vs @ConfigurationProperties

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:35`

**Visual:**

Expand the single `@Value` field into several related settings and place a typed configuration object beside them.

**Script:**

One scalar is easy to inject directly. A coherent group of settings needs a stronger consumption boundary.

**Purpose:**

Move from scalar injection to the choice between `@Value` and typed configuration binding.

### Scene 6 — Choose the Consumption Model, Not the Winning Source

**Time:** `04:35–05:20`

**Visual:**

Show a comparison table: isolated scalar, structured namespace, relaxed binding, validation, metadata, and SpEL. Put `@Value` on the scalar side and `@ConfigurationProperties` on the structured side.

**Script:**

`@Value` is lightweight for isolated values and can evaluate SpEL. `@ConfigurationProperties` is designed for a coherent namespace with relaxed binding, typed conversion, validation, and metadata. This choice controls how code consumes the value; it does not change which property source won earlier in the pipeline.

**Purpose:**

Teach the boundary between scalar injection and structured typed configuration consumption.

## SpEL Support and the @Value Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:20–05:30`

**Visual:**

Highlight the SpEL row in the comparison table and isolate it on the `@Value` side.

**Script:**

One capability is intentionally different: `@Value` can evaluate expressions, while configuration-properties binding remains data-oriented.

**Purpose:**

Clarify the expression-language boundary between the two consumption models.

### Scene 7 — Keep SpEL at the `@Value` Boundary

**Time:** `05:30–06:15`

**Visual:**

Show a small `@Value` SpEL expression on the left and a plain `@ConfigurationProperties` record on the right. Label the record path `data binding`, not `expression evaluation`.

**Script:**

SpEL can be useful when one injected value genuinely needs a small expression. `@ConfigurationProperties` intentionally treats configuration as data and does not evaluate SpEL. Keep expressions rare and visible. If a group of settings represents a configuration contract, a typed object is easier to reason about than logic hidden inside strings.

**Purpose:**

Define the SpEL boundary and reinforce data-oriented structured configuration.
