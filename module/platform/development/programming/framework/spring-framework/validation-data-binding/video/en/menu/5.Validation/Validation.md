---
video:
  url: ""
---

# Spring Validation and Error Reporting

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

## Validator Contract and supports(...)

<!-- VIDEO_SECTION -->

### Scene 1 — Rule object plus error collector

**Time:** `00:00–01:05`

**Visual:**

Show a small `AccountValidator` with `supports(Account.class)` on the left and `validate(account, errors)` on the right. Animate invalid username and age rules into `errors.rejectValue(...)` calls instead of exceptions.

**Script:**

Spring’s `Validator` keeps validation independent of HTTP, persistence, or a UI toolkit. Its two responsibilities are intentionally small. `supports` answers whether this validator is appropriate for a target type. `validate` evaluates one object and records ordinary invalid state in an `Errors` collector. Think of it as a rule object paired with a structured error sink. Compatibility belongs in `supports`; business rules belong in `validate`.

**Purpose:**

Establish the Spring Validator contract and separate type compatibility from the rules being evaluated.

## Errors as the Validation Error Collector

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Move the focus from the validator to the `Errors` parameter and expand it into a `BindingResult` when binding context is present.

**Script:**

The validator decides what is wrong, but the next question is how those failures stay useful to later layers.

**Purpose:**

Bridge rule evaluation to the structured collector that preserves validation and binding evidence.

### Scene 1 — One result can preserve two failure stages

**Time:** `01:15–02:20`

**Visual:**

Show the real module validation demo: bind `age="15"`, freeze on `errorsAfterBinding=0`, then run the validator and reveal `age.tooYoung`, `errorsAfterValidation=1`, and `bindingFailure=false`.

**Script:**

`Errors` is the common collector that validators write into, while `BindingResult` adds binding-specific details around that same error model. In this module’s runtime demo, “15” converts successfully into the integer property, so binding reports zero errors. Only after `binder.validate()` runs does the result gain `age.tooYoung`, and the field error reports `bindingFailure=false`. The same structured result can therefore preserve which stage produced the problem instead of flattening everything into one message.

**Purpose:**

Use real evidence to show how BindingResult extends the Errors model without erasing the distinction between binding and validation failures.

## Nested Object Validation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:20–02:30`

**Visual:**

Expand a flat Account card into an Order with a nested ShippingAddress object.

**Script:**

Real objects are rarely flat. When a child object owns its own rules, the error path needs to follow that object graph too.

**Purpose:**

Move from top-level validation to nested validation while preserving field identity.

### Scene 1 — Delegate without losing the field path

**Time:** `02:30–03:35`

**Visual:**

Show `pushNestedPath("shippingAddress")`, invoke an `AddressValidator`, then `popNestedPath()` inside `finally`. Animate `rejectValue("street", ...)` becoming `shippingAddress.street`.

**Script:**

Nested validation uses the mutable path cursor on `Errors`. A parent validator can push `shippingAddress`, delegate to a validator that only knows about addresses, and then restore the path in a `finally` block. While that path is active, a rejection for `street` is recorded as `shippingAddress.street`. Delegate rules that truly belong to the child; keep cross-object invariants in the validator that has enough context to evaluate them.

**Purpose:**

Show safe nested-validator composition and why restoring the nested path is essential for correct error identity.

## ValidationUtils as Supporting Infrastructure

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:45`

**Visual:**

Collapse repeated null/blank checks into two small helper calls labeled `ValidationUtils`.

**Script:**

Some validation code repeats the same small checks or delegation ceremony. Spring provides helpers for those cases without introducing a new validation model.

**Purpose:**

Position ValidationUtils as convenience infrastructure rather than an alternate rule engine.

### Scene 1 — Helpers for common operations

**Time:** `03:45–04:40`

**Visual:**

Show `rejectIfEmptyOrWhitespace`, `rejectIfEmpty`, and `invokeValidator`. Highlight the automatic `supports` compatibility check before delegated invocation.

**Script:**

`ValidationUtils` is useful for small, recognizable operations: reject an empty or blank field, or invoke another Spring validator with the expected compatibility check. It can also pass hints to a `SmartValidator`. Once a domain rule needs several values, branching, or meaningful domain language, ordinary Java code in the validator is clearer than trying to force the rule into a helper call.

**Purpose:**

Teach where ValidationUtils reduces ceremony and where explicit domain rule code remains the better choice.

## SmartValidator and Validation Hints

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:40–04:50`

**Visual:**

Add a third input to the validator call labeled “hints”, with a `RegistrationChecks.class` card.

**Script:**

Sometimes the same target type participates in more than one validation scenario. Spring models that extra context as hints.

**Purpose:**

Bridge the basic Validator contract to contextual validation.

### Scene 1 — Context without changing the target type

**Time:** `04:50–05:50`

**Visual:**

Show `binder.validate(RegistrationChecks.class)` flowing through a `SmartValidator`. Then show a Bean Validation-backed adapter interpreting the class hint as a validation group, with a note that other validators may interpret or ignore hints differently.

**Script:**

`SmartValidator` extends the basic contract with `validate(Object, Errors, Object... hints)`. Spring itself does not assign one universal meaning to every hint. Bean Validation-backed adapters commonly treat class hints as validation groups, which lets the caller request a scenario-specific constraint set. Depend on those semantics only when the chosen validator documents them. A hint is context for validation; it does not become a binding rule.

**Purpose:**

Explain what validation hints are, how Bean Validation commonly uses them, and why their meaning belongs to the concrete validator.

## Message Code Resolution

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:50–06:00`

**Visual:**

Take the stable code `tooYoung` and fan it into a descending list of increasingly general message-code candidates.

**Script:**

The errors we have recorded still need user-facing text, but the validator should not hard-code one English sentence as the rule’s identity.

**Purpose:**

Move from structured errors to the code hierarchy used for later localization.

### Scene 1 — Specific first, general fallback

**Time:** `06:00–07:00`

**Visual:**

Reveal candidates `tooYoung.account.age`, `tooYoung.age`, `tooYoung.int`, and `tooYoung` in order. Show the same resolver handling a binding code such as `typeMismatch`.

**Script:**

`MessageCodesResolver` expands a stable error code into ordered lookup candidates. A field error can try an object-and-field-specific code first, then fall back through broader field, type, and generic codes. That lets one validator stay locale-independent while applications customize messages at different levels. Binding failures such as `typeMismatch` can use the same resolution strategy, which keeps message lookup consistent across error sources.

**Purpose:**

Show how stable semantic codes become an ordered localization lookup without changing validation logic.

## MessageSource and i18n Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:00–07:10`

**Visual:**

Pass the list of resolvable error codes across a boundary line into a `MessageSource` box owned by Core Container.

**Script:**

Code resolution prepares the candidates. Turning those candidates into localized text is the next owner’s job.

**Purpose:**

Mark the handoff from this module’s structured error model to Core Container localization.

### Scene 1 — End this module at resolvable errors

**Time:** `07:10–08:10`

**Visual:**

Show the pipeline `Validator/DataBinder -> ObjectError/FieldError -> codes + arguments -> MessageSource -> localized text`. Add a side bridge from `LocalValidatorFactoryBean.setValidationMessageSource(...)` into the same MessageSource.

**Script:**

This module ends with structured errors, message-code candidates, and arguments. `MessageSource` owns resolving those codes into localized text for a locale, and that broader lifecycle belongs to Core Container. `LocalValidatorFactoryBean` can connect Bean Validation interpolation to a Spring `MessageSource`, but that integration does not move i18n ownership into the validation module. MVC and WebFlux then decide how localized errors appear in their own transport responses.

**Purpose:**

Clarify the i18n ownership boundary while showing the legitimate Bean Validation integration bridge.
