---
video:
  url: ""
---

# DataBinder Orchestration

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

## What DataBinder Orchestrates

<!-- VIDEO_SECTION -->

### Scene 1 — The coordinator, not the owner of every rule

**Time:** `00:00–01:05`

**Visual:**

Animate an input-values box through field policy, conversion/formatting/property access, target state, `BindingResult`, and optional validators. Keep `DataBinder` as the frame around the flow rather than as each inner component.

**Script:**

`DataBinder` is the reusable coordinator that brings earlier mechanisms together. It accepts an input-value model, applies binding policy, delegates conversion and property access, updates or constructs typed state, and collects failures in a `BindingResult`. Validators can then add domain-rule errors to that same result. The binder orchestrates these pieces; it does not replace the converter, property accessor, or validator that owns each individual rule.

**Purpose:**

Give learners a single orchestration model for DataBinder while preserving the responsibility boundaries established in earlier chapters.

## Binding Flow from Input Values to BindingResult

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Zoom into the property-binding path and place `bind(PropertyValues)` at the entrance and `getBindingResult()` at the exit.

**Script:**

With the coordinator in view, we can now follow one actual property-binding operation from incoming values to evidence.

**Purpose:**

Move from DataBinder’s responsibility map to the concrete bind-and-inspect workflow.

### Scene 1 — A type mismatch becomes structured evidence

**Time:** `01:15–02:25`

**Visual:**

Run the binding-failure scenario from the module’s real `/validation-binding/validation/binding-vs-validation` experiment. Show `age="not-a-number"` entering an `int` property that starts at `41`. Freeze on the response fields: `targetAgeBeforeBinding=41`, `targetAgeAfterBinding=41`, `targetAgeUnchanged=true`, `hasErrors=true`, a type-mismatch code, rejected value `not-a-number`, and `bindingFailure=true`.

**Script:**

For property binding, `bind(PropertyValues)` is the main entry point. Here the binder receives text that cannot become an integer. Instead of pretending the assignment succeeded, Spring records a `FieldError` in the binding result. The target age remains unchanged, the rejected text is preserved, and `bindingFailure` is true. That result is the evidence we should inspect. A partially updated object by itself is never proof that the whole input operation succeeded.

**Purpose:**

Use real runtime evidence to demonstrate the bind-to-BindingResult flow and the meaning of a direct conversion failure.

## Property and Setter Binding

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:25–02:35`

**Visual:**

Replace the failed input with a mutable command object whose writable setters light up as potential binding targets.

**Script:**

That demo used the default property-binding model. Its convenience comes from applying input to an object that already exists.

**Purpose:**

Bridge the generic binding flow to the semantics and risks of property/setter binding.

### Scene 1 — Existing object, writable surface

**Time:** `02:35–03:35`

**Visual:**

Show an `AccountForm` before binding, then apply `name=Ada` and `age=37` through JavaBean properties. Highlight `setAllowedFields("name", "age")`, then briefly show `initDirectFieldAccess()` as a different, explicit mode.

**Script:**

Property binding starts with an existing target and applies values through writable properties, normally following JavaBean setter semantics. Nested paths and conversion participate as needed. Because the object already exists, successful assignments may happen before another field later fails, so the binding result matters more than a snapshot of the object. Direct field access is available, but it changes what counts as writable and can bypass setter behavior, so it should be an intentional choice.

**Purpose:**

Explain how property binding mutates existing state and why the writable surface and non-atomic assignment behavior matter.

## Constructor Binding

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:45`

**Visual:**

Fade out the existing mutable target and replace it with constructor parameters waiting for values before an object exists.

**Script:**

Property binding begins with an object. Constructor binding reverses that order: Spring must resolve input before the target can be created.

**Purpose:**

Contrast mutation of an existing target with construction from resolved arguments.

### Scene 1 — Input shaped by constructor parameters

**Time:** `03:45–04:50`

**Visual:**

Show `new DataBinder(null, "account")`, `setTargetType(...)`, and `construct(ValueResolver)`. Animate the resolver being asked only for constructor parameter names, then reveal the constructed target from `BindingResult.getTarget()`.

**Script:**

In Spring Framework 6.1, plain `DataBinder` supports constructor binding through `construct(ValueResolver)`. The binder knows the target type, asks the resolver for the constructor values it needs, converts them, and exposes the resulting target through the binding result. This path fits immutable or purpose-built input models well because the accepted shape is expressed by construction. Property-binding settings such as unknown fields and allowed fields do not redefine which constructor arguments exist.

**Purpose:**

Show the constructor-binding lifecycle and distinguish its input surface from property-binding field policies.

## Declarative Binding in Spring Framework 6.1

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:50–05:00`

**Visual:**

Place constructor binding and property binding side by side, then add a switch labeled `declarativeBinding=true` over the property-binding lane.

**Script:**

Spring 6.1 adds a setting that changes the default posture of property binding while leaving constructor binding available.

**Purpose:**

Introduce declarative binding as an explicit surface-control policy rather than another validation feature.

### Scene 1 — Opt in to writable properties

**Time:** `05:00–06:10`

**Visual:**

Run the real safe-binding setup: `setDeclarativeBinding(true)`, `setAllowedFields("displayName")`, input `displayName=Ada` plus `role=ADMIN`. Show `displayName` applied, `role` unchanged at `USER`, and `suppressedFields=[role]`.

**Script:**

Declarative binding says that property binding should occur only when allowed fields are explicitly configured. In this module’s demo, `displayName` is allowed and `role` is not. Both values arrive, but only the display name changes. The role remains `USER`, and the binding result records `role` as suppressed. That is a write-surface decision made before validation. A field can be valid domain state and still be intentionally unbindable.

**Purpose:**

Use real runtime evidence to demonstrate the Spring 6.1 declarative-binding contract and suppressed-field behavior.

## Required, Unknown, Invalid, Allowed, and Disallowed Fields

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:10–06:20`

**Visual:**

Expand the declarative-binding screen into a policy matrix with five rows: required, unknown, invalid, allowed, disallowed.

**Script:**

Allowed fields control the writable surface, but DataBinder exposes several other policies, and each answers a different question.

**Purpose:**

Move from one safe-binding setting to the full property-binding policy vocabulary.

### Scene 1 — Do not collapse policies into one “strict mode”

**Time:** `06:20–07:30`

**Visual:**

Walk down the matrix: missing configured required field -> `required`; unknown field -> ignored by default; inaccessible nested field -> not ignored by default; allowed patterns -> permitted surface; disallowed patterns -> blocked surface. Mark all five as property-binding policies.

**Script:**

Required fields say that a named value must be present in this bind operation. Unknown fields name no target property and are ignored by default. Invalid fields refer to target state that cannot currently be accessed and are not ignored by default. Allowed fields form a positive binding surface; disallowed fields exclude patterns from it. These policies are related but not interchangeable, and they apply to property binding. For untrusted input, an allow-list is easier to review because newly added writable properties do not silently become accepted.

**Purpose:**

Give each DataBinder field policy a distinct meaning and connect allow-listing to maintainable input-surface control.

## Conversion, Custom Editors, Validators, and Error Processing

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:30–07:40`

**Visual:**

Turn the policy matrix into extension sockets around the DataBinder box.

**Script:**

Once the binding surface is defined, applications still need controlled extension points for representation, validation, and error shaping.

**Purpose:**

Bridge binding policy to the main DataBinder extension mechanisms.

### Scene 1 — Extend the right responsibility

**Time:** `07:40–08:50`

**Visual:**

Label four sockets: `setConversionService`, `registerCustomEditor`, validators, and `BindingErrorProcessor`/MessageCodesResolver. Show type conversion flowing to ConversionService, legacy text editing to PropertyEditor, domain rules to validators, and missing/property-access failures to the error processor.

**Script:**

`DataBinder` is extensible, but each hook has a separate job. Use a `ConversionService` for reusable typed conversion and formatting. Register a custom `PropertyEditor` mainly for legacy integration. Configure validators for domain rules after binding. A `BindingErrorProcessor` decides how missing required values and property-access exceptions become binding errors, while a `MessageCodesResolver` expands those codes for later lookup. Keeping those jobs distinct makes a binding failure explainable instead of mysterious.

**Purpose:**

Close the DataBinder chapter with a map from each customization need to the extension point that actually owns it.
