---
video:
  url: ""
---

# Validation, Binding, Conversion, and Formatting

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

## Why These Mechanisms Exist

<!-- VIDEO_SECTION -->

### Scene 1 — One input, four different jobs

**Time:** `00:00–01:05`

**Visual:**

Animate one raw input card, `age = "37"`, through four labeled stages: Binding chooses the target property, Conversion produces an `int`, Assignment updates typed state, and Validation checks the resulting rule. Add a second callout showing a locale-aware date formatter beside the conversion stage.

**Script:**

When external data reaches an application, it usually does not already look like the Java object we want. Spring keeps several jobs separate. Binding decides where a named value belongs. Conversion changes the Java type. Formatting handles human-facing text when locale or field presentation matters. Validation asks whether the resulting state is acceptable. If we keep those questions separate, a converter or validator can be reused outside one web request, and an error can be traced to the stage that actually failed.

**Purpose:**

Establish the pipeline mental model and give each mechanism one clear responsibility before later chapters explore the individual APIs.

## Validation vs Data Binding

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Keep the pipeline on screen and zoom into two adjacent checkpoints labeled “Can this input populate typed state?” and “Is that state acceptable?”

**Script:**

That pipeline gives us the first boundary to test: an input can fail before a business rule ever runs, or it can bind perfectly and still be invalid.

**Purpose:**

Carry the pipeline model into the distinction between binding failures and validation failures.

### Scene 1 — Same field, two different failures

**Time:** `01:15–02:25`

**Visual:**

Show two runs side by side. Run A binds `age = "not-a-number"` to an `int` and highlights a `FieldError` with `bindingFailure=true`. Run B binds `age = "15"`, shows zero binding errors, then runs `AdultRegistrationValidator` and highlights `age.tooYoung` with `bindingFailure=false`.

**Script:**

Here are two inputs that look similar but fail for different reasons. “Not-a-number” cannot become an integer, so the binding stage records a type-mismatch field error and the validator does not need to explain it. “15” converts and is assigned successfully. Only after validation runs do we get the domain rule `age.tooYoung`. The practical rule is to diagnose the failure where it originates. Binding tells us whether input can populate typed state; validation tells us whether that typed state is acceptable.

**Purpose:**

Use real module evidence to make the binding-versus-validation boundary observable rather than purely definitional.

## Conversion vs Formatting

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:25–02:35`

**Visual:**

Slide the validation checkpoint aside and place two transformation cards in focus: `String -> OrderId` and `LocalDate <-> localized text`.

**Script:**

Once we know a value must change shape, there is another choice: is this a stable type transformation, or is the text itself part of the user-facing presentation?

**Purpose:**

Bridge from failure stages to the difference between structural conversion and presentation-aware formatting.

### Scene 1 — Type meaning versus text presentation

**Time:** `02:35–03:40`

**Visual:**

On the left, show a `ConversionService` call turning `"42"` into `Integer.class`. On the right, show `1234.5` rendered as `1,234.5` for US and `1.234,5` for Germany, with a `Locale` badge entering a `Formatter`.

**Script:**

General conversion answers a type question: how does one Java value become another Java type? A formatter answers a presentation question: how should a typed value be parsed from or printed as text for this field and locale? A stable identifier such as `String -> OrderId` is a good converter problem. A decimal or date whose text changes by locale is a formatter problem. Keeping locale-sensitive text out of a context-free converter makes the policy easier to reason about.

**Purpose:**

Give learners a decision rule for choosing conversion or formatting based on whether presentation context affects the transformation.

## Module Boundary and Neighboring Owners

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:40–03:50`

**Visual:**

Zoom out from the four mechanisms to a module map with this module in the center and Core Container, MVC, WebFlux, and Aspect around it.

**Script:**

These contracts are reusable, but they do not own every lifecycle that uses them. The last step in this foundation is to mark where this module hands control to its neighbors.

**Purpose:**

Connect the reusable contracts to the repository curriculum boundaries before the learner enters implementation details.

### Scene 1 — Know where the reusable core stops

**Time:** `03:50–05:00`

**Visual:**

Highlight this module owning `BeanWrapper`, `ConversionService`, formatter SPIs, `Errors`, `Validator`, `DataBinder`, and Bean Validation integration. Then highlight handoff arrows: `MessageSource` to Core Container, `WebDataBinder/@InitBinder` to MVC/WebFlux, and proxy mechanics for method validation to Aspect.

**Script:**

This module owns the reusable contracts for property access, conversion, formatting, validation, binding, and Spring’s integration with Jakarta Bean Validation. It stops before transport-specific controller lifecycles. Core Container owns the broader `MessageSource` and i18n infrastructure. MVC and WebFlux own how requests create and use web binders. Method-validation wiring appears here, while proxy interception mechanics belong to the Aspect topic. That boundary matters because `DataBinder` itself is not a web-only API.

**Purpose:**

Prevent later examples from attributing web, container, or proxy behavior to the lower-level validation and binding contracts.
