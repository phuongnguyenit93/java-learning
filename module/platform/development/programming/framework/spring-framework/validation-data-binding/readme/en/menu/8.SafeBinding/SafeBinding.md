<a id="back-to-top"></a>

# Safe Binding and End-to-End Decisions

## Menu
- [Binding Security Is an Input-Surface Problem](#binding-security-model)
- [Constrained Input Models](#safe-input-models)
- [Constructor, Declarative, and Allowed-Field Controls](#safe-binding-controls)
- [Binding Security vs Validation Rules](#binding-validation-separation)
- [Choosing Conversion, Formatting, Validator, Bean Validation, and DataBinder](#mechanism-selection)
- [End-to-End Binding and Validation Pipeline](#validation-binding-end-to-end)
- [Handoffs to Core Container, MVC, and WebFlux](#validation-binding-handoffs)

## <a id="binding-security-model">Binding Security Is an Input-Surface Problem</a>

<details>
<summary>Click for details</summary>

Binding security is primarily about **which input is allowed to influence object state**. Validation answers a later question: whether the state is acceptable. If an attacker can bind a field that should never have been externally writable, a validator may be perfectly satisfied with the resulting value and the security failure has already happened.

Consider a domain type with properties such as `role`, `creditLimit`, or `approved`. Those fields may be valid domain state while still being inappropriate for mass assignment from external input. The safe design question is therefore not "can this value pass validation?" but "should this input channel be able to write this member at all?"

```text
external names/values
        ↓
binding surface decision   ← security boundary
        ↓
typed object state
        ↓
validation rules           ← correctness boundary
```

This distinction applies even outside HTTP. Any generic mapping of less-trusted name/value input into writable application objects needs an explicit input surface.

</details>

- [Back to top](#back-to-top)

---

## <a id="safe-input-models">Constrained Input Models</a>

<details>
<summary>Click for details</summary>

A dedicated input model narrows the binding surface before binder configuration is considered. Instead of binding directly into a rich domain entity, define a command/DTO whose constructor or writable properties represent only the values that the use case accepts.

```java
public final class ProfileUpdateForm {
    private String displayName;
    private ZoneId timezone;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ZoneId getTimezone() {
        return timezone;
    }

    public void setTimezone(ZoneId timezone) {
        this.timezone = timezone;
    }
}
```

The absence of `role`, `accountStatus`, and internal identifiers from this type is a strong guarantee: ordinary property binding cannot accidentally expose members that do not exist on the input model.

Constrained models also make validation easier to reason about. Input-shape rules can target the command, while domain objects remain responsible for their own invariants. Mapping from the validated command into the domain model becomes an explicit application step rather than a side effect of generic property assignment.

The trade-off is additional types and mapping code. That cost is usually worthwhile at trust boundaries because the accepted input contract becomes visible in Java instead of existing only as runtime configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="safe-binding-controls">Constructor, Declarative, and Allowed-Field Controls</a>

<details>
<summary>Click for details</summary>

Spring 6.1 provides several controls that complement constrained input models:

1. **constructor binding** resolves only values needed by the selected constructor path;
2. **declarative binding** (`setDeclarativeBinding(true)`) always permits constructor binding but requires an explicit `allowedFields` configuration before property binding occurs;
3. **allowed fields** form an allow-list for property binding.

```java
DataBinder binder = new DataBinder(target, "profile");
binder.setDeclarativeBinding(true);
binder.setAllowedFields("displayName", "timezone");
```

These controls solve related but different problems. Constructor binding expresses input through construction shape. Declarative mode changes the default posture of property binding. `allowedFields` specifies the property surface that remains writable.

`disallowedFields` can block known-sensitive properties, but an allow-list is generally easier to maintain for untrusted input: adding a new writable property later does not automatically make it bindable. Treat these patterns as part of the application's input contract and cover important cases with tests.

</details>

- [Back to top](#back-to-top)

---

## <a id="binding-validation-separation">Binding Security vs Validation Rules</a>

<details>
<summary>Click for details</summary>

Binding policy and validation policy act at different points in the flow and should remain separate.

```text
"role=ADMIN" arrives
        ↓
Is `role` bindable?        → binding policy
        ↓ yes
Can the raw value convert? → conversion/binding
        ↓ yes
Is ADMIN valid state?      → validation/domain policy
```

If the use case should never accept a role change, the correct answer is to reject `role` at the binding surface, not to invent a validator that tries to recognize "unauthorized" values after the property has already been exposed.

Likewise, `requiredFields` is a binding rule: it says a named property must be supplied with a value that passes `DataBinder`'s required-field presence/emptiness check. Absent values and values that check treats as empty produce `required`; this is still different from a validator rule such as "end date is required when status is CLOSED", which evaluates the meaning of the resulting object.

Keeping the layers separate improves failure reporting too. Binding failures can use codes such as `required` or `typeMismatch`, while domain validation can use semantic codes such as `period.end.requiredWhenClosed`.

</details>

- [Back to top](#back-to-top)

---

## <a id="mechanism-selection">Choosing Conversion, Formatting, Validator, Bean Validation, and DataBinder</a>

<details>
<summary>Click for details</summary>

Choose the mechanism by the problem it owns rather than by whichever API is already nearby:

| Problem | Primary mechanism |
| --- | --- |
| Reusable typed transformation | `ConversionService` / `Converter` |
| Locale- or presentation-sensitive parse/print | `Formatter` / `FormatterRegistry` |
| Programmatic object/domain rules | Spring `Validator` |
| Annotation/provider-based constraints and groups | Jakarta Bean Validation through Spring integration |
| Applying input values, field policy, collecting binding failures | `DataBinder` |

A common mistake is to put conversion inside a validator. If `"37"` must become an `int`, that is conversion. The validator should receive the typed state and decide whether `37` is acceptable. The reverse mistake is trying to make conversion reject a semantically invalid but type-correct value such as age `-1`; that belongs to validation.

`DataBinder` coordinates these mechanisms but does not replace them. It can host a `ConversionService` and validators because a binding flow needs both; each abstraction should still keep its own responsibility.

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-binding-end-to-end">End-to-End Binding and Validation Pipeline</a>

<details>
<summary>Click for details</summary>

A safe end-to-end flow is easier to reason about when each phase has one job:

```text
raw external values
        ↓
choose accepted input surface
        ↓
choose constructor and/or property binding path
        ↓
resolve + convert / format values as part of binding
        ↓
construct / assign typed state
        ↓
BindingResult records binding failures
        ↓
Spring Validator / Bean Validation
        ↓
ObjectError / FieldError + message codes
```

```java
ProfileUpdateForm command = new ProfileUpdateForm();
DataBinder binder = new DataBinder(command, "profile");
binder.setDeclarativeBinding(true);
binder.setAllowedFields("displayName", "timezone");
binder.setConversionService(conversionService);
binder.addValidators(profileValidator);

binder.bind(propertyValues);
if (!binder.getBindingResult().hasErrors()) {
    binder.validate();
}

BindingResult result = binder.getBindingResult();
```

Whether validation should run when binding errors already exist is an application/infrastructure decision; the important point is that both kinds of failure remain distinguishable in one structured result. Do not discard binding evidence and then validate a partially populated object as though input processing succeeded.

The pipeline ends here with structured errors and resolvable codes. Localization and transport presentation are explicit downstream handoffs.

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-binding-handoffs">Handoffs to Core Container, MVC, and WebFlux</a>

<details>
<summary>Click for details</summary>

This module owns the reusable validation, binding, conversion, formatting, and Bean Validation integration contracts. Three neighboring owners take over after those contracts have done their work:

- **Spring Core Container** owns `MessageSource` and the broader i18n infrastructure that resolves error codes into localized text.
- **Spring MVC** owns `WebDataBinder`, `@InitBinder`, controller argument binding, HTTP request-to-object lifecycle, and MVC-specific validation/error behavior.
- **Spring WebFlux** owns the corresponding reactive controller binding and validation lifecycle.

Method validation has one additional boundary: this module explains `MethodValidator`, `MethodValidationAdapter`, and `MethodValidationPostProcessor` as validation infrastructure, while **Spring AOP/Aspect** owns proxy/interceptor mechanics.

These are not arbitrary documentation boundaries. They keep the reusable core model usable in service, UI, batch, test, or custom infrastructure code without requiring a web stack, while allowing each transport module to teach its own lifecycle deeply.

When following an error downstream, use the ownership chain rather than duplicating behavior here: produce structured binding/validation errors in this module, localize through Core Container, and let the owning transport decide how those errors are presented to a client.

</details>

- [Back to top](#back-to-top)
