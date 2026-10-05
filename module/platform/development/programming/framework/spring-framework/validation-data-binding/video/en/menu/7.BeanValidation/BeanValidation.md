---
video:
  url: ""
---

# Jakarta Bean Validation Integration

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

## Spring vs Jakarta Bean Validation Responsibility

<!-- VIDEO_SECTION -->

### Scene 1 — Specification semantics, Spring integration

**Time:** `00:00–01:05`

**Visual:**

Place Jakarta Bean Validation on the left with `@NotBlank`, `@Min`, groups, `@Valid`, and executable validation. Place Spring on the right with `Validator`, `Errors`, `DataBinder`, bean lifecycle, and method-validation infrastructure. Draw integration arrows between them.

**Script:**

Jakarta Bean Validation defines the constraint model: annotations, the Jakarta validator API, groups, cascading, executable validation, and provider behavior. Spring does not redefine those semantics. Spring integrates the provider with its own validation contracts, binding results, application context, and method-validation infrastructure. Keep that ownership line visible: whether a constraint is valid belongs to Jakarta and its provider; how those violations enter Spring’s error model belongs to the integration layer here.

**Purpose:**

Establish the boundary between Bean Validation specification behavior and Spring-specific integration.

## SpringValidatorAdapter

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Zoom into the integration arrow and label it `SpringValidatorAdapter`.

**Script:**

The first bridge lets infrastructure that already speaks Spring `Validator` and `Errors` consume Jakarta constraint results directly.

**Purpose:**

Move from the ownership boundary to the adapter that translates provider violations into Spring validation terms.

### Scene 1 — Adapt ConstraintViolation into Errors

**Time:** `01:15–02:15`

**Visual:**

Show a Jakarta `Validator` wrapped by `SpringValidatorAdapter`. Feed a constrained command into `validate(command, errors)`, then transform `ConstraintViolation` cards into Spring field and object error cards.

**Script:**

`SpringValidatorAdapter` wraps a Jakarta `Validator`, implements Spring’s `SmartValidator`, and also exposes the native Jakarta operations. Through the Spring validation methods, it asks the provider for constraint violations and adapts them into `Errors`. Property violations can become field errors with rejected values and codes, while object-level violations remain object errors. The adapter is a bridge; it is not a second implementation of Bean Validation.

**Purpose:**

Show how Jakarta constraint evidence becomes compatible with Spring’s structured error model.

## LocalValidatorFactoryBean

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:25`

**Visual:**

Pull back from one adapter instance to an ApplicationContext configuration with a single `LocalValidatorFactoryBean`.

**Script:**

Wrapping a validator is useful, but an application also needs one configured provider that participates cleanly in the Spring container.

**Purpose:**

Bridge per-validator adaptation to container-friendly Bean Validation bootstrap.

### Scene 1 — One bootstrap, several useful views

**Time:** `02:25–03:30`

**Visual:**

Show a `@Bean LocalValidatorFactoryBean` producing three interfaces: Spring `Validator/SmartValidator`, Jakarta `Validator`, and `ValidatorFactory`. Add callouts for SpringConstraintValidatorFactory and optional `setValidationMessageSource`.

**Script:**

`LocalValidatorFactoryBean` is Spring’s ApplicationContext-friendly bootstrap for a Bean Validation provider. One configured object can be consumed as a Spring validator, a Jakarta validator, or a validator factory. By default, Spring can create constraint validators through its `BeanFactory`, which allows dependency injection into custom validators without changing the provider’s validation semantics. It can also connect message interpolation to a Spring `MessageSource`.

**Purpose:**

Explain why LocalValidatorFactoryBean is the central integration point for provider bootstrap and Spring container services.

## Validation Groups and Spring Hints

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Split one constrained command into two scenarios labeled Create and Update.

**Script:**

The same constrained type can participate in different scenarios. Bean Validation calls those scenarios groups, and Spring can pass them through its hint mechanism.

**Purpose:**

Connect Bean Validation groups to the SmartValidator hint model introduced earlier.

### Scene 1 — Select a scenario without changing the object type

**Time:** `03:40–04:40`

**Visual:**

Show `binder.validate(Create.class)` reaching a Bean Validation-backed `SmartValidator`. Contrast `Create.class` and `Update.class`, then show `@Valid` separately as cascading rather than group selection.

**Script:**

When Spring invokes a Bean Validation-backed `SmartValidator`, class-valued hints can select validation groups. That lets the same type use a different constraint set for create and update scenarios. Groups should represent real scenarios; if two workflows expose very different data, separate command models may be simpler. And do not confuse groups with `@Valid`: groups select which constraints run, while `@Valid` requests cascaded validation of associated objects.

**Purpose:**

Clarify the relationship among Spring hints, Bean Validation groups, and cascaded validation.

## Executable Method Validation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:40–04:50`

**Visual:**

Move the constraint annotations from object fields to a service method’s parameters and return value.

**Script:**

So far the validation target has been an object. Bean Validation can also treat a method invocation boundary itself as the constrained target.

**Purpose:**

Shift the learner from object validation to executable parameter and return-value validation.

### Scene 1 — Validate the call boundary

**Time:** `04:50–05:50`

**Visual:**

Show `PricingService.quote(@NotBlank sku, @Positive quantity)` with `@Positive` on the return value. Animate checks before invocation for parameters and after invocation for the return value; show `@Valid` cascading into an object parameter.

**Script:**

Executable validation checks constraints on method parameters and return values. Parameter constraints evaluate the arguments about to be passed, while return-value constraints evaluate the produced result. Cascading can validate object parameters or return values when `@Valid` is present. This is different from writing a validator call inside the method body: the method boundary is the validation target and infrastructure can apply the rule consistently.

**Purpose:**

Define executable validation and distinguish it from ordinary object validation and manual validation inside a method.

## MethodValidator, MethodValidationAdapter, and Result Model

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:50–06:00`

**Visual:**

Take raw method constraint violations and pass them through a `MethodValidationAdapter` into a structured result diagram.

**Script:**

Raw constraint violations are useful, but Spring 6.1 adds a framework-level model that organizes them around method parameters and return values.

**Purpose:**

Bridge Bean Validation executable semantics to Spring’s structured method-validation result model.

### Scene 1 — Adapt invocation failures into Spring results

**Time:** `06:00–07:05`

**Visual:**

Show `determineValidationGroups`, `validateArguments`, and `validateReturnValue` producing `MethodValidationResult`. Expand one parameter result into message-resolvable errors and a cascaded object-error structure.

**Script:**

Spring Framework 6.1 introduced `MethodValidator` and `MethodValidationAdapter`. The adapter uses a Jakarta validator underneath but returns a Spring `MethodValidationResult`. Errors are grouped around the affected method parameter or return value and can expose `MessageSourceResolvable` information; cascaded object violations can also carry binding-style error structures. This is useful when infrastructure needs structured evidence rather than a raw set of provider violations.

**Purpose:**

Show what Spring 6.1 adds around executable validation: grouping, result structure, message codes, and parameter identity.

## MethodValidationPostProcessor and the AOP Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:05–07:15`

**Visual:**

Wrap the validated service in a Spring proxy outline and place `MethodValidationPostProcessor` in the container setup.

**Script:**

A result model is only useful if application infrastructure can apply validation at the right call boundary. Spring’s convenient container wiring uses method-validation advice.

**Purpose:**

Connect method-validation semantics and results to the container component that installs runtime interception.

### Scene 1 — Wiring here, proxy mechanics elsewhere

**Time:** `07:15–08:25`

**Visual:**

Show `MethodValidationPostProcessor` detecting a type-level `@Validated` bean and installing advice. Compare default `ConstraintViolationException` with `setAdaptConstraintViolations(true)` producing `MethodValidationException` backed by Spring’s result model. Fade proxy internals toward an “Aspect module” boundary.

**Script:**

`MethodValidationPostProcessor` finds eligible beans, by default through type-level `@Validated`, and installs method-validation advice backed by a Bean Validation provider. In Spring Framework 6.1.14, the default failure is a Jakarta `ConstraintViolationException`. If adapted constraint violations are enabled, Spring raises a `MethodValidationException` around its structured result model instead. This chapter owns the validation wiring and result behavior. Advisor ordering, proxy type, and self-invocation mechanics belong to the Aspect module.

**Purpose:**

Explain the post-processor’s validation behavior, exception modes, and the explicit boundary to AOP proxy mechanics.
