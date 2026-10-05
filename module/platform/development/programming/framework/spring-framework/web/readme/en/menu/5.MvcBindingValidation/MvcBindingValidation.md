<a id="back-to-top"></a>

# MVC Binding and Validation Integration

## Menu
- [MVC Data Binding, Conversion, and Formatting Lifecycle](#mvc-binding-lifecycle)
- [WebDataBinder and @InitBinder Customization](#web-data-binder-and-init-binder)
- [Object Validation and BindingResult](#validation-and-binding-result)
- [Built-In MVC Method Validation in Spring Framework 6.1](#mvc-method-validation-6-1)
- [MethodArgumentNotValidException vs HandlerMethodValidationException](#validation-exception-paths)
- [Class-Level @Validated and the AOP Method-Validation Boundary](#validated-aop-boundary)
- [Safe Data Binding and Allowed Fields](#safe-binding)

## <a id="mvc-binding-lifecycle">MVC Data Binding, Conversion, and Formatting Lifecycle</a>

<details>
<summary>Click for details</summary>

MVC data binding converts untyped request input into Java values that a handler can work with. The pipeline combines property binding, type conversion/formatting, and optional validation.

For a bindable object, MVC creates a `WebDataBinder`, applies global binding initialization, applies matching `@InitBinder` methods, binds request values, performs conversion, and then validates when requested.

```text
request values
→ WebDataBinder
→ conversion / formatting
→ target object
→ validation
→ handler method
```

Binding errors and validation errors are related but not identical: conversion can fail before a constraint is evaluated. Controllers should not assume every invalid input becomes the same exception or error shape.

The reusable binder/conversion/validation abstractions belong to the dedicated validation/data-binding module; this chapter focuses on how MVC places them in the controller lifecycle.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-data-binder-and-init-binder">WebDataBinder and @InitBinder Customization</a>

<details>
<summary>Click for details</summary>

`WebDataBinder` is MVC's web-aware binder for controller input. It controls property access, field markers/defaults, allowed/disallowed fields, conversion, validation, and related binding behavior.

`@InitBinder` methods customize binders in controller scope. They are useful when one controller needs a specific formatter, validator, allowed-field policy, or editor without changing binding rules application-wide.

Keep binder customization narrow. A local date format or allowlist is reasonable; business rules that require repositories or remote services often belong in application/domain validation rather than binder setup.

`@ControllerAdvice` can also contribute binder initialization across controllers, but global binder rules are powerful. Make them predictable because they affect handler signatures that may not visibly reference the advice.

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-and-binding-result">Object Validation and BindingResult</a>

<details>
<summary>Click for details</summary>

Object validation commonly occurs when a bindable argument is annotated with `@Valid` or `@Validated`. After binding, MVC invokes the configured validation infrastructure and records failures.

For supported validated arguments such as `@ModelAttribute`, `@RequestBody`, or `@RequestPart`, an `Errors` or `BindingResult` parameter declared **immediately after** that argument lets the controller inspect its binding/validation errors locally rather than immediately propagating an exception.

The important distinction is:

```text
binding error
→ request value could not be bound/converted

validation error
→ Java value exists but violates a declared rule
```

Controllers should preserve that distinction in user-facing feedback where useful. A malformed date and a well-formed date outside an allowed range are different failures.

Do not turn `BindingResult` handling into a manual replacement for all error policy. REST endpoints often benefit from centralized error responses instead.

</details>

- [Back to top](#back-to-top)

---

## <a id="mvc-method-validation-6-1">Built-In MVC Method Validation in Spring Framework 6.1</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 added built-in MVC method validation for controller method parameters and return values. This is distinct from validating one object during argument resolution.

Method validation becomes relevant when constraint annotations are declared directly on method parameters or on the method for its return value. MVC can then validate the method invocation as a whole and report violations through `HandlerMethodValidationException`. Once method validation applies, it supersedes the individual argument-validation path for participating parameters and also evaluates nested constraints reached through `@Valid`.

For **method parameters**, `@Valid` by itself does not trigger method validation; it primarily requests nested/object validation through the argument resolver that owns that parameter. A direct parameter constraint such as `@NotNull` or `@Min` makes the method a candidate for argument method validation. Return-value validation has a related but distinct rule: a constraint annotation **or `@Valid` on the method return value** makes the return value a candidate for MVC method validation.

Method validation can still work with local `Errors`/`BindingResult` parameters. The controller method is invoked only when **all** validation errors belong to method parameters that each have an `Errors` or `BindingResult` immediately after them; otherwise MVC raises `HandlerMethodValidationException`.

This built-in path is integrated with MVC's handler invocation and error model, which avoids requiring an AOP proxy for ordinary controller method validation in Spring 6.1.

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-exception-paths">MethodArgumentNotValidException vs HandlerMethodValidationException</a>

<details>
<summary>Click for details</summary>

Two validation exceptions often appear similar but represent different lifecycle points.

`MethodArgumentNotValidException` is typically raised when an individual argument resolver validates a bindable/request-body argument and that argument fails validation.

`HandlerMethodValidationException` represents **unhandled** method-level validation failures across controller parameters or return values in Spring MVC 6.1. It can contain validation results grouped by the kind of method parameter. If every method-validation error belongs to a parameter that has an `Errors` or `BindingResult` immediately after it, MVC can instead supply those errors locally and still invoke the controller method.

```text
individual argument validation without a local Errors/BindingResult
→ MethodArgumentNotValidException

method-level validation with violations that are not all handled locally
→ HandlerMethodValidationException
```

Error handlers should account for both when an API uses both styles. Do not flatten them prematurely if the application needs to preserve field/object errors versus parameter-level constraint information.

</details>

- [Back to top](#back-to-top)

---

## <a id="validated-aop-boundary">Class-Level @Validated and the AOP Method-Validation Boundary</a>

<details>
<summary>Click for details</summary>

Spring's older/general method-validation mechanism uses `@Validated` with AOP method validation. That remains useful for service beans and other proxied components.

For controllers in Spring Framework 6.1, class-level `@Validated` changes the path: method validation is delegated to the AOP proxy rather than MVC's built-in method-validation support. If the goal is to use the new MVC-integrated behavior, the controller should not rely on class-level `@Validated`.

This difference affects exception types, lifecycle position, and which component owns validation.

Use the two mechanisms deliberately:

```text
MVC controller built-in method validation
→ handler-method lifecycle

generic @Validated method validation
→ AOP proxy lifecycle
```

</details>

- [Back to top](#back-to-top)

---

## <a id="safe-binding">Safe Data Binding and Allowed Fields</a>

<details>
<summary>Click for details</summary>

Data binding can become a mass-assignment vulnerability when external request parameters are allowed to write properties that were never intended to be client-controlled.

The safe default is to design dedicated input models with only writable fields needed by the endpoint. When property binding is used, `WebDataBinder` can restrict writable fields with an allowlist and other binding controls.

Do not bind request parameters directly onto persistence entities or rich domain objects merely because their property names happen to match. Fields such as `role`, `status`, `ownerId`, or pricing flags may then become attacker-controlled.

```text
HTTP input model
→ explicit allowed data
→ application validation/mapping
→ domain model
```

Binding safety is a web-input boundary. It complements authorization; an allowlist does not replace checking whether the current user may perform the requested operation.

</details>

- [Back to top](#back-to-top)
