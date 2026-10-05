<a id="back-to-top"></a>

# Spring Validation and Error Reporting

## Menu
- [Validator Contract and supports(...)](#validator-contract)
- [Errors as the Validation Error Collector](#errors-contract)
- [Nested Object Validation](#nested-validation)
- [ValidationUtils as Supporting Infrastructure](#validation-utils)
- [SmartValidator and Validation Hints](#smart-validator-hints)
- [Message Code Resolution](#message-code-resolution)
- [MessageSource and i18n Boundary](#validation-i18n-boundary)

## <a id="validator-contract">Validator Contract and supports(...)</a>

<details>
<summary>Click for details</summary>

Spring's `Validator` exists so validation rules do not have to depend on HTTP, a UI toolkit, or a persistence technology. The contract has two responsibilities: `supports(Class<?>)` answers whether a validator is appropriate for a type, and `validate(Object, Errors)` evaluates one object and records failures in the supplied `Errors` collector.

`supports(...)` is a type-compatibility check, not a business rule. A typical implementation uses `SomeType.class.isAssignableFrom(clazz)` so subclasses can be validated too. The actual rules belong in `validate(...)`, which should report failures with stable error codes rather than throw an exception for ordinary invalid input.

```java
final class AccountValidator implements Validator {
    @Override
    public boolean supports(Class<?> type) {
        return Account.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Account account = (Account) target;
        if (account.username() == null || account.username().isBlank()) {
            errors.rejectValue("username", "username.required");
        }
        if (account.age() < 18) {
            errors.rejectValue("age", "age.minimum", new Object[] {18}, null);
        }
    }
}
```

The useful mental model is **rule object + error collector**. The validator decides what is invalid; `Errors` decides how those failures are represented. That separation lets the same validator participate in a `DataBinder`, a service-layer check, or a focused unit test.

Spring Framework 6.1 also added `Validator.validateObject(Object)` for focused validation that does not need a binding process. It creates and returns a simple `Errors` result, which can be inspected directly or converted into an exception with `failOnError(...)`:

```java
Errors errors = accountValidator.validateObject(account);

if (errors.hasErrors()) {
    // inspect the structured validation errors
}

accountValidator.validateObject(account)
        .failOnError(IllegalArgumentException::new);
```

This convenience path intentionally has a smaller result model than a binding-capable `BindingResult`: the simple `Errors` implementation used by `validateObject(...)` does not support nested paths. When validation needs nested-path state or binding-specific evidence, call the regular `validate(Object, Errors)` method with an appropriate `Errors` implementation such as `BeanPropertyBindingResult`.

### References

- Spring Framework 6.1.14 API — `Validator`

</details>

- [Back to top](#back-to-top)

---

## <a id="errors-contract">Errors as the Validation Error Collector</a>

<details>
<summary>Click for details</summary>

`Errors` is the write-oriented validation context. It carries an object name, a nested-path cursor, access to current field values, and methods such as `reject(...)` and `rejectValue(...)` for registering object-level and field-level failures. A validator normally receives an existing `Errors` instance and appends its findings.

`BindingResult` extends `Errors`. It adds binding-specific information such as the target object, raw field values, suppressed fields, and message-code resolution. `DataBinder.getBindingResult()` returns this richer result. This matters because binding can fail **before** business validation runs: for example, converting `"abc"` to an `int` can create a `typeMismatch` field error, and later validators operate on the same result object.

```java
DataBinder binder = new DataBinder(new Account("", 0), "account");
binder.addValidators(new AccountValidator());
binder.validate();

BindingResult result = binder.getBindingResult();
if (result.hasErrors()) {
    result.getFieldErrors().forEach(error ->
        System.out.println(error.getField() + " -> " + error.getCode()));
}
```

Do not treat `BindingResult` as a localized-message container. It stores structured errors and message-code candidates. Converting those codes into user-facing text is a downstream `MessageSource` concern.

</details>

- [Back to top](#back-to-top)

---

## <a id="nested-validation">Nested Object Validation</a>

<details>
<summary>Click for details</summary>

Validation often follows an object graph rather than one flat object. Spring supports this through nested paths on `Errors`. A parent validator can temporarily move the error context into a child property and delegate to a validator dedicated to that child type.

```java
final class OrderValidator implements Validator {
    private final AddressValidator addressValidator = new AddressValidator();

    @Override
    public boolean supports(Class<?> type) {
        return Order.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Order order = (Order) target;
        if (order.shippingAddress() == null) {
            errors.rejectValue("shippingAddress", "address.required");
            return;
        }

        errors.pushNestedPath("shippingAddress");
        try {
            addressValidator.validate(order.shippingAddress(), errors);
        }
        finally {
            errors.popNestedPath();
        }
    }
}
```

While the nested path is active, `rejectValue("street", ...)` is recorded for `shippingAddress.street`. The `try/finally` pattern is important because nested-path state is mutable; failing to restore it can make later errors point at the wrong field.

Delegate when the child concept owns its own rules. Keep cross-object invariants in the validator that has enough context to evaluate them. This avoids duplicating child rules while still allowing a parent to express rules such as "billing and shipping countries must match".

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-utils">ValidationUtils as Supporting Infrastructure</a>

<details>
<summary>Click for details</summary>

`ValidationUtils` is a convenience layer, not another validation model. It provides small helpers for common operations: invoking another Spring `Validator` and rejecting fields that are empty or empty after trimming whitespace.

```java
@Override
public void validate(Object target, Errors errors) {
    ValidationUtils.rejectIfEmptyOrWhitespace(
        errors, "username", "username.required");

    ValidationUtils.rejectIfEmpty(
        errors, "countryCode", "country.required");
}
```

`ValidationUtils.invokeValidator(...)` performs the expected `supports(...)` check before invoking the validator and can pass validation hints to a `SmartValidator`. This is useful when one validator composes another and you want the standard compatibility check instead of casting and calling it manually.

Use these helpers when they make the intent clearer. Do not force complex domain rules into utility calls; once a rule needs several values, branching, or domain terminology, ordinary Java code inside a validator is easier to read and test.

### References

- Spring Framework 6.1.14 API — `ValidationUtils`

</details>

- [Back to top](#back-to-top)

---

## <a id="smart-validator-hints">SmartValidator and Validation Hints</a>

<details>
<summary>Click for details</summary>

`SmartValidator` extends `Validator` with contextual **validation hints**. Its main overload is `validate(Object, Errors, Object... validationHints)`. The base Spring contract does not prescribe what a hint means; the concrete validator decides whether and how to interpret it.

The most common use is Jakarta Bean Validation groups. `SpringValidatorAdapter` and `LocalValidatorFactoryBean` can treat `Class<?>` hints as Bean Validation groups, so the caller can ask for a stricter or scenario-specific rule set without changing the target object's type.

```java
interface RegistrationChecks {}

DataBinder binder = new DataBinder(command, "command");
binder.addValidators(localValidatorFactoryBean);
binder.validate(RegistrationChecks.class);
```

Hints are deliberately loose so Spring validators can support other contextual models. A validator is allowed to ignore hints and behave like normal `validate(...)`. Code should therefore depend on hint semantics only when the chosen validator documents them.

`SmartValidator` also provides field-value validation support. That can be useful when infrastructure needs to validate a candidate value without first mutating a complete object, but it is still validation, not data binding.

</details>

- [Back to top](#back-to-top)

---

## <a id="message-code-resolution">Message Code Resolution</a>

<details>
<summary>Click for details</summary>

Spring error codes are identifiers, not final messages. When code calls `errors.rejectValue("age", "tooYoung")`, the `BindingResult` asks its `MessageCodesResolver` to expand that stable code into progressively more general candidates.

With `DefaultMessageCodesResolver`, a field error can produce candidates such as:

```text
tooYoung.account.age
tooYoung.age
tooYoung.int
tooYoung
```

The ordering lets applications define a very specific message when needed and fall back to a broader message otherwise. Object errors follow the same idea with object-specific and generic codes. `BindingResult.resolveMessageCodes(...)` exposes the same resolution mechanism programmatically.

This layer is important for two reasons. First, validators can remain independent of a locale. Second, binding failures such as `typeMismatch` can participate in the same message lookup strategy as domain validation errors.

Prefer stable semantic codes (`customer.email.invalid`) over complete English sentences. A code can survive wording and locale changes; a sentence cannot.

### References

- Spring Framework 6.1.14 API — `MessageCodesResolver` and `DefaultMessageCodesResolver`

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-i18n-boundary">MessageSource and i18n Boundary</a>

<details>
<summary>Click for details</summary>

This module stops at **structured errors plus resolvable message codes**. `MessageSource` is the component that maps those codes and arguments to localized text for a `Locale`, and its lifecycle/configuration belongs to the Spring Core Container module.

The handoff can be pictured as:

```text
Validator / DataBinder
        ↓
ObjectError / FieldError
        ↓
message-code candidates + arguments
        ↓
MessageSource (Core Container)
        ↓
localized text
```

There is an integration bridge: `LocalValidatorFactoryBean` can be configured with `setValidationMessageSource(...)` so Bean Validation message interpolation can use a Spring `MessageSource`. That does not move ownership of `MessageSource` into validation; it only connects the validation provider to the application's localization infrastructure.

Likewise, deciding how localized errors are rendered in an HTTP response, form, or reactive handler is outside this module. MVC and WebFlux own those transport-specific presentation lifecycles.

</details>

- [Back to top](#back-to-top)
