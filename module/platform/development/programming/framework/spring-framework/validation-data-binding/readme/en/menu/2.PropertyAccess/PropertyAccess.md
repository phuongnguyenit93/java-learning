<a id="back-to-top"></a>

# Property Access and Binding State

## Menu
- [Property Paths and Nested Access](#property-path-model)
- [BeanWrapper and the Property Access Abstraction](#bean-wrapper-role)
- [BindingResult and Errors](#binding-result-model)
- [ObjectError and FieldError](#object-field-error-model)
- [Direct Errors vs Policy-Dependent Binding Cases](#binding-error-policy)

## <a id="property-path-model">Property Paths and Nested Access</a>

<details>
<summary>Click for details</summary>

Binding needs a stable way to describe *where* a value belongs. Spring uses property paths for that job. A simple path such as `name` identifies one property on the target object. A nested path such as `address.city` walks through an object graph. Indexed or mapped paths can address container elements when the underlying property-access implementation supports them.

The useful mental model is a path through writable state:

```text
customer
  └─ address
       └─ city

property path: "address.city"
```

The path is metadata about target state; it is not itself a conversion or validation rule. Before a value can be assigned, Spring still has to determine whether the property exists, whether it is writable, whether the nested graph is accessible, and whether the input value can be converted to the property's declared type.

```java
public final class CustomerForm {
    private Address address = new Address();

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
}

public final class Address {
    private String city;

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
}
```

Here, `address.city` is meaningful because both segments can be navigated. If `address` is `null`, the behavior depends on the property-access/binder configuration. Some Spring accessors can auto-grow nested paths; `DataBinder` also has policy for invalid nested paths. Do not assume every null intermediate object is silently created.

The practical benefit of property paths is that binding, error reporting, and validation can refer to the same stable field identity. A later `FieldError` for `address.city` can therefore point back to exactly the state location the binding operation attempted to populate.

</details>

- [Back to top](#back-to-top)

---

## <a id="bean-wrapper-role">BeanWrapper and the Property Access Abstraction</a>

<details>
<summary>Click for details</summary>

`BeanWrapper` is Spring's central low-level abstraction for working with standard JavaBeans. It can inspect property descriptors, query whether a property is readable or writable, get and set values, navigate nested properties, perform type conversion, and participate in `PropertyEditor` registration.

Most application code should not treat `BeanWrapper` as the high-level binding workflow. `DataBinder` commonly uses this property-access infrastructure underneath. The distinction matters: `BeanWrapper` manipulates properties; `DataBinder` adds binding policy, binding results, validation integration, allowed/disallowed fields, required-field handling, and other orchestration.

```java
CustomerForm form = new CustomerForm();

BeanWrapper wrapper =
        PropertyAccessorFactory.forBeanPropertyAccess(form);

wrapper.setPropertyValue("address.city", "Da Nang");

String city = (String) wrapper.getPropertyValue("address.city");
boolean writable = wrapper.isWritableProperty("address.city");
```

Because `BeanWrapper` extends Spring's configurable property-access and type-conversion contracts, it can also use a `ConversionService`. This explains why property assignment can involve conversion without making `BeanWrapper` itself the owner of the application's conversion policy.

Two pitfalls are worth separating:

- a path can be syntactically plausible but still point to an unknown or unreadable/unwritable property;
- a path can refer to a real nested property but fail because an intermediate object is inaccessible.

Those are property-access problems. Whether a binder ignores or reports some of them is a higher-level `DataBinder` policy discussed later.

### References

- [Spring Framework 6.1.14 `BeanWrapper`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/beans/BeanWrapper.html)
- [Spring Framework 6.1.14 `PropertyAccessor`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/beans/PropertyAccessor.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="binding-result-model">BindingResult and Errors</a>

<details>
<summary>Click for details</summary>

`Errors` is Spring's common contract for storing and exposing data-binding and validation errors. `BindingResult` extends `Errors` with binding-specific analysis and integration support such as access to the target object, raw field values, suppressed fields, registered property editors, model-building support, and message-code resolution.

That inheritance relationship gives a useful separation:

```text
Errors
  └─ error registration/query model

BindingResult extends Errors
  └─ binding outcome + binding-specific inspection
```

A validator can depend on `Errors` because that common contract is sufficient for reporting and querying its validation failures. Code that needs the richer outcome and integration details of a bind operation can work with `BindingResult`.

```java
CustomerForm form = new CustomerForm();
BindingResult result =
        new BeanPropertyBindingResult(form, "customer");

result.rejectValue(
        "address.city",
        "city.required",
        "City is required"
);

if (result.hasFieldErrors("address.city")) {
    FieldError error = result.getFieldError("address.city");
    System.out.println(error.getCode());
}
```

The important idea is that Spring preserves failures as structured data instead of reducing them immediately to strings or throwing every problem as an exception. That structure supports later message-code resolution, localization, transport-specific rendering, or programmatic decisions.

`BindingResult` should therefore be read as the *result model* of binding and validation work. It is not the mechanism that decides which input source a web controller uses; that orchestration belongs to the relevant web stack.

### References

- [Spring Framework 6.1.14 `Errors`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/validation/Errors.html)
- [Spring Framework 6.1.14 `BindingResult`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/validation/BindingResult.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="object-field-error-model">ObjectError and FieldError</a>

<details>
<summary>Click for details</summary>

Spring represents validation and binding problems primarily as `ObjectError` and `FieldError`.

An `ObjectError` applies to the object as a whole. Use it when the rule cannot be assigned meaningfully to one property, such as "start date must be before end date" or "the combined state of these fields is inconsistent." A `FieldError` is more specific: it associates the problem with a field path such as `quantity` or `address.city`.

```java
errors.reject(
        "dateRange.invalid",
        "Start date must be before end date"
);

errors.rejectValue(
        "quantity",
        "quantity.positive",
        "Quantity must be positive"
);
```

Both error types are `MessageSourceResolvable`. They can carry one or more message codes, arguments, and a default message. This is why the error model can remain stable while presentation is localized later by `MessageSource`.

For binding failures, `FieldError` can also retain the rejected value and binding-failure flag. That distinction helps consumers tell whether a field failed while applying external input or failed a subsequent validation rule.

Do not encode a fully localized sentence as the only identity of a rule. Prefer stable error codes such as `quantity.positive`, then allow message resolution to map those codes to user-facing text later. The localization mechanism itself belongs to the Core Container/i18n boundary; this module owns the structured error and message-code side of the contract.

</details>

- [Back to top](#back-to-top)

---

## <a id="binding-error-policy">Direct Errors vs Policy-Dependent Binding Cases</a>

<details>
<summary>Click for details</summary>

Not every suspicious input name produces the same kind of failure. Spring deliberately exposes policy switches because applications differ in how strict property binding should be.

Three cases are especially easy to confuse:

- **type mismatch**: the property exists, but the submitted value cannot be converted to its required type. This is a direct property-binding failure.
- **unknown field**: the input names a property that does not exist on the target. `DataBinder` ignores unknown fields by default (`ignoreUnknownFields = true`), but applications can turn that off.
- **invalid field**: the path corresponds to target state but is not currently accessible, for example because a nested path cannot be traversed or auto-grown. A standard `DataBinder` enables nested-path auto-growth by default when possible, so a merely `null` intermediate object is not automatically an invalid field. If access still fails, `DataBinder` does not ignore invalid fields by default (`ignoreInvalidFields = false`).

Required fields form another policy layer. A field participates in this check only when the binder has been configured with `setRequiredFields(...)`; Java nullability alone does not automatically make a bind value required. The check is stricter than name presence: absent values, `null`, blank or whitespace-only `String` values, and empty or first-blank `String[]` values are treated as missing and produce the default `required` error.

```java
DataBinder binder = new DataBinder(new CustomerForm(), "customer");
binder.setRequiredFields("address.city");
binder.setIgnoreUnknownFields(false);
binder.setIgnoreInvalidFields(false);
```

These switches apply to property binding through `bind(PropertyValues)`. They are not constructor-binding rules. Constructor binding asks its `ValueResolver` only for values needed by constructor parameters, which is why "unknown field" policy is not the right model for that path.

This distinction becomes important in later safe-binding design: strictness about unknown input does not by itself define the allowed write surface. Allowed/disallowed fields and declarative binding solve that separate problem.

### References

- [Spring Framework 6.1.14 `DataBinder`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/validation/DataBinder.html)

</details>

- [Back to top](#back-to-top)
