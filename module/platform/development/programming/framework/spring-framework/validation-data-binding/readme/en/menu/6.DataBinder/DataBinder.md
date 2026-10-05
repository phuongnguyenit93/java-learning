<a id="back-to-top"></a>

# DataBinder Orchestration

## Menu
- [What DataBinder Orchestrates](#data-binder-role)
- [Binding Flow from Input Values to BindingResult](#data-binding-flow)
- [Property and Setter Binding](#property-binding-model)
- [Constructor Binding](#constructor-binding-model)
- [Declarative Binding in Spring Framework 6.1](#declarative-binding-61)
- [Required, Unknown, Invalid, Allowed, and Disallowed Fields](#binding-field-policies)
- [Conversion, Custom Editors, Validators, and Error Processing](#data-binder-extension-points)

## <a id="data-binder-role">What DataBinder Orchestrates</a>

<details>
<summary>Click for details</summary>

`DataBinder` is the reusable coordinator that turns an input-value model into typed object state while collecting failures in a `BindingResult`. It does not define HTTP request semantics; MVC's `WebDataBinder` and WebFlux build transport-specific lifecycles on top of this lower-level contract.

A binder brings several pieces from earlier chapters together:

```text
input values
   ↓
field policy
   ↓
conversion / formatting / property access
   ↓
target object
   ↓
BindingResult
   ↓
optional Validator execution
```

```java
AccountForm form = new AccountForm();
DataBinder binder = new DataBinder(form, "account");
binder.setConversionService(conversionService);
binder.addValidators(accountValidator);
```

The binder owns orchestration, not every rule. Converters decide how values change type, property access decides how values reach the target, validators decide whether resulting state is acceptable, and the `BindingResult` preserves both binding and validation errors.

### References

- Spring Framework 6.1.14 API — `DataBinder`

</details>

- [Back to top](#back-to-top)

---

## <a id="data-binding-flow">Binding Flow from Input Values to BindingResult</a>

<details>
<summary>Click for details</summary>

For property binding, `bind(PropertyValues)` is the main entry point. Before values reach the target, `DataBinder` applies required-field checks and allowed/disallowed-field filtering. It then delegates conversion and property assignment to its binding infrastructure. Conversion or property-access failures become field errors rather than ordinary successful assignments.

```java
MutablePropertyValues values = new MutablePropertyValues();
values.add("name", "Ada");
values.add("age", "not-a-number");

DataBinder binder = new DataBinder(new AccountForm(), "account");
binder.bind(values);
binder.validate();

BindingResult result = binder.getBindingResult();
```

Binding and validation are separate phases. `bind(...)` can register errors such as `required` or `typeMismatch`; `validate(...)` then invokes configured validators and adds domain-rule failures to the same result. Code that only calls `bind(...)` has not automatically performed validation.

Constructor binding uses a different entry point, `construct(ValueResolver)`, because it must obtain constructor arguments before a target instance exists. Both flows converge on `getBindingResult()` as the place to inspect failures.

Treat `BindingResult` as the evidence of the whole orchestration step. A partially mutated target should never be mistaken for proof that all input was accepted correctly.

</details>

- [Back to top](#back-to-top)

---

## <a id="property-binding-model">Property and Setter Binding</a>

<details>
<summary>Click for details</summary>

Property binding starts with an existing target object and applies incoming values through writable properties. With the default bean-property model, Spring follows JavaBean property semantics, supports nested paths, and converts each incoming value toward the property's declared type before assignment.

```java
AccountForm form = new AccountForm();
DataBinder binder = new DataBinder(form, "account");
binder.setAllowedFields("name", "age");

binder.bind(new MutablePropertyValues(Map.of(
    "name", "Ada",
    "age", "37"
)));
```

This model is convenient for mutable command objects, but its input surface is the set of writable properties that binding policy permits. That is why field policy is a security concern, not just configuration polish.

`initDirectFieldAccess()` switches the binder to direct field access instead of JavaBean property access. That is a deliberate alternative for unusual models, not the default recommendation: it changes what constitutes a writable member and can bypass setter behavior.

Property binding also differs from constructor binding in timing. The object already exists, so successful assignments can occur before another field later fails. Use `BindingResult` to judge the binding operation as a whole rather than assuming object construction is atomic.

</details>

- [Back to top](#back-to-top)

---

## <a id="constructor-binding-model">Constructor Binding</a>

<details>
<summary>Click for details</summary>

Constructor binding creates the target from input values instead of mutating an already-created object. In Spring Framework 6.1, plain `DataBinder` exposes this through `construct(DataBinder.ValueResolver)`. Before calling it, the binder needs a target type and normally has no target instance yet.

```java
DataBinder binder = new DataBinder(null, "account");
binder.setTargetType(ResolvableType.forClass(AccountCommand.class));

binder.construct(new DataBinder.ValueResolver() {
    @Override
    public Object resolveValue(String name, Class<?> type) {
        return input.get(name);
    }

    @Override
    public Set<String> getNames() {
        return input.keySet();
    }
});

BindingResult result = binder.getBindingResult();
AccountCommand command = (AccountCommand) result.getTarget();
```

Spring resolves constructor arguments by name, with support for constructor metadata such as `@ConstructorProperties` and retained parameter names. `DataBinder.NameResolver` is an extension point when input names need mapping. Constructor conversion/validation failures are reported through the binding result.

The key security property is that constructor binding asks only for values needed by the selected constructor path. Settings such as unknown-field handling, required fields, allowed fields, and disallowed fields are property-binding policies; they do not redefine which constructor arguments exist.

Constructor binding fits immutable or purpose-built input models especially well because the accepted surface is expressed in the constructor itself.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-binding-61">Declarative Binding in Spring Framework 6.1</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 added `setDeclarativeBinding(true)` to make the intended binding surface explicit. In declarative mode, constructor binding remains available, while property binding is performed only when `allowedFields` has been configured.

```java
DataBinder binder = new DataBinder(command, "command");
binder.setDeclarativeBinding(true);
binder.setAllowedFields("displayName", "timezone");
```

The mental model is **opt in to writable properties**. Without declarative mode, property binding is allowed by default subject to ordinary field policies. With declarative mode enabled, an unlisted writable property is not implicitly part of the property-binding contract.

Declarative binding is not validation. A field can be intentionally bindable and still contain an invalid value; conversely, a perfectly valid domain value should not become bindable unless the input contract intends to expose it.

It is also not an MVC-only feature. MVC and WebFlux may configure a binder as part of their controller lifecycles, but the underlying setting belongs to core `DataBinder`.

### References

- Spring Framework 6.1.14 API — `DataBinder.setDeclarativeBinding(boolean)`

</details>

- [Back to top](#back-to-top)

---

## <a id="binding-field-policies">Required, Unknown, Invalid, Allowed, and Disallowed Fields</a>

<details>
<summary>Click for details</summary>

Field policies answer different questions and should not be collapsed into one "strict mode":

- **required fields**: a configured required field produces a `required` binding error by default when it is absent or its submitted value is treated as empty by the required-field check; this includes `null`, blank or whitespace-only `String` values, and empty or first-blank `String[]` values;
- **unknown fields**: input names with no corresponding property are ignored by default (`ignoreUnknownFields=true`); turning this off makes them binding failures;
- **invalid fields**: names that correspond to a property but cannot be accessed, for example when a nested path cannot be traversed or auto-grown, are not ignored by default (`ignoreInvalidFields=false`); standard `DataBinder` auto-grows nested paths by default when possible, so a `null` intermediate object alone is not necessarily an invalid field;
- **allowed fields**: property patterns explicitly permitted for binding; the default is all fields;
- **disallowed fields**: property patterns excluded from binding; the default is none.

```java
binder.setRequiredFields("name");
binder.setIgnoreUnknownFields(false);
binder.setAllowedFields("name", "profile.*");
```

These settings apply to property binding through `bind(PropertyValues)`, not to constructor binding through `construct(...)`, which resolves only constructor values it needs.

For untrusted external input, an allow-list is easier to reason about than a deny-list: newly added writable properties do not silently become accepted merely because nobody remembered to add them to a block list. Required fields are also not domain validation rules; they mean "this binding operation must supply a non-empty value according to DataBinder's required-field check", not "the final object satisfies the business invariant".

</details>

- [Back to top](#back-to-top)

---

## <a id="data-binder-extension-points">Conversion, Custom Editors, Validators, and Error Processing</a>

<details>
<summary>Click for details</summary>

`DataBinder` is intentionally extensible because applications differ in representation and validation policy. The main extension points have separate jobs:

- `setConversionService(...)` supplies modern type conversion and formatting support;
- `registerCustomEditor(...)` integrates legacy JavaBeans `PropertyEditor` behavior when necessary;
- `addValidators(...)`, `setValidator(...)`, and related methods configure validation after binding;
- `BindingErrorProcessor` decides how missing required values and property-access exceptions become binding errors;
- `MessageCodesResolver` expands error codes into lookup candidates.

The default binding error processor maps a missing required field to code `required` and property access failures such as type conversion problems to field errors. Applications can replace it, but doing so changes error representation and should be justified by a real integration requirement.

```java
DataBinder binder = new DataBinder(target, "target");
binder.setConversionService(conversionService);
binder.addValidators(domainValidator);
binder.setMessageCodesResolver(new DefaultMessageCodesResolver());
```

Prefer `ConversionService` for reusable typed conversion. Use custom editors mainly when integrating older code that is already built around `PropertyEditor`. Keep validation in validators and input-surface policy in binder configuration; mixing these responsibilities makes failures harder to interpret.

</details>

- [Back to top](#back-to-top)
