<a id="back-to-top"></a>

# MVC Exception Resolution and Problem Responses

## Menu
- [HandlerExceptionResolver Chain](#handler-exception-resolver-chain)
- [@ExceptionHandler and @ControllerAdvice](#exception-handler-and-controller-advice)
- [ResponseStatusException and Exception-to-Status Mapping](#response-status-errors)
- [ErrorResponse and ProblemDetail](#error-response-and-problem-detail)
- [ResponseEntityExceptionHandler](#response-entity-exception-handler)
- [Validation Errors in the MVC Error Pipeline](#validation-error-responses)
- [Spring Boot and Repository Global-Handler Boundaries](#mvc-error-boundaries)

## <a id="handler-exception-resolver-chain">HandlerExceptionResolver Chain</a>

<details>
<summary>Click for details</summary>

Spring MVC treats exception handling as an ordered resolution stage in the request pipeline. When handler processing throws, the `DispatcherServlet` asks configured `HandlerExceptionResolver` implementations whether they can translate the failure into an MVC response outcome.

The standard resolver set includes support for controller `@ExceptionHandler` methods, status-based exception mapping, and Framework defaults for well-known MVC exceptions.

```text
exception
→ resolver 1
→ resolver 2
→ ...
→ resolved response OR propagate
```

Ordering matters. A broad custom resolver placed too early can swallow exceptions that a more specific Framework resolver would have translated better.

Resolvers also operate under HTTP response constraints. If output is already committed, error handling may be unable to replace status, headers, or body cleanly.

</details>

- [Back to top](#back-to-top)

---

## <a id="exception-handler-and-controller-advice">@ExceptionHandler and @ControllerAdvice</a>

<details>
<summary>Click for details</summary>

`@ExceptionHandler` lets a controller define methods that handle exceptions from its request-handling methods. `@ControllerAdvice` can contribute such handlers across multiple controllers and is useful for shared API error policy.

Local controller handlers and applicable advice are resolved according to MVC's exception-handler selection rules, including exception type specificity and advice ordering.

Keep handlers focused on translating a failure into an HTTP-facing outcome. They should not hide programming defects or turn every unexpected exception into `200 OK`.

`@ControllerAdvice` is powerful global infrastructure. Narrow it by package, annotation, assignable controller type, or exception scope when one policy should not affect the whole application.

Exception handlers can return `ResponseEntity`, `ProblemDetail`, views, or other MVC-supported outcomes, so they still participate in the normal return-value pipeline.

</details>

- [Back to top](#back-to-top)

---

## <a id="response-status-errors">ResponseStatusException and Exception-to-Status Mapping</a>

<details>
<summary>Click for details</summary>

`ResponseStatusException` represents an exception that carries an HTTP status and optional reason. It is useful when application code needs to raise an HTTP-facing failure without defining a dedicated exception class for every status.

Spring MVC also supports status mapping through `@ResponseStatus` on exception types. The resolver infrastructure can translate these declarations into the HTTP response.

Use status-bearing exceptions deliberately. A status is part of the web contract, so throwing `ResponseStatusException` deep inside a domain model couples that model to HTTP.

Application/domain exceptions can instead remain transport-neutral and be mapped in MVC advice.

Avoid using status mapping as a substitute for a structured error body. Clients often need a stable error shape in addition to the numeric status.

</details>

- [Back to top](#back-to-top)

---

## <a id="error-response-and-problem-detail">ErrorResponse and ProblemDetail</a>

<details>
<summary>Click for details</summary>

`ProblemDetail` is Spring's representation of a standardized HTTP problem-details body. It carries fields such as status, title, detail, type, and instance, and can be extended with additional properties.

`ErrorResponse` is a contract for exceptions that expose HTTP status, headers, and a `ProblemDetail` body. Several Spring web exceptions implement or participate in this model, which gives applications a common way to customize error responses.

The value is consistency:

```text
different MVC exceptions
→ common error-response contract
→ predictable problem response
```

Do not expose internal stack traces, database details, credentials, or sensitive identifiers through problem properties. The public error contract should help clients act without leaking implementation details.

Problem details standardize representation, not business error taxonomy. Applications still need stable domain/error codes when clients must distinguish business cases.

</details>

- [Back to top](#back-to-top)

---

## <a id="response-entity-exception-handler">ResponseEntityExceptionHandler</a>

<details>
<summary>Click for details</summary>

`ResponseEntityExceptionHandler` is a convenient base class for `@ControllerAdvice` that wants to handle Spring MVC's standard exceptions with a consistent `ResponseEntity`/problem-detail style.

It centralizes dispatch to protected `handle...` methods for common MVC failures and eventually builds the response through shared hooks. Applications can override the narrow method for a specific exception or the common body-building path.

This is preferable to copying the Framework's entire list of exception handlers into an advice class.

Use inheritance carefully: overriding a handler means taking responsibility for preserving appropriate status, headers, and semantics. A custom error envelope should not accidentally erase important headers or convert client errors into server errors.

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-error-responses">Validation Errors in the MVC Error Pipeline</a>

<details>
<summary>Click for details</summary>

Validation can enter the MVC error pipeline through more than one exception type.

`MethodArgumentNotValidException` commonly represents validation failure for a specific argument processed by an argument resolver, such as a validated request body or bindable object.

Spring Framework 6.1 can also raise `HandlerMethodValidationException` for method-level constraints on controller parameters or return values.

An API-wide error handler should understand both shapes if both validation styles are used. Field/object errors may carry different information from parameter-level method-validation results.

Do not return an opaque "validation failed" string for every case. Preserve enough structured information for a client to identify the invalid input while avoiding leakage of internal object structure.

</details>

- [Back to top](#back-to-top)

---

## <a id="mvc-error-boundaries">Spring Boot and Repository Global-Handler Boundaries</a>

<details>
<summary>Click for details</summary>

Spring MVC owns the Framework exception-resolution mechanics. Spring Boot adds application-level defaults around error dispatch and error endpoints, while this repository's `project-build/springboot-runtime/exception-handler-servlet` module is a support/runtime artifact rather than the curriculum owner.

That means this module teaches:

- `HandlerExceptionResolver`;
- `@ExceptionHandler`/`@ControllerAdvice`;
- status mapping;
- `ErrorResponse`, `ProblemDetail`, and Framework exception handling.

It does not teach Boot's full error-controller/auto-configuration model, Feign-specific handlers, Mongo-specific failures, or repository utility conventions as if they were core Spring MVC.

When an application uses those layers, understand which layer produced the final response before changing MVC configuration.

</details>

- [Back to top](#back-to-top)
