<a id="back-to-top"></a>

# Jakarta Bean Validation Integration

## Menu
- [Spring vs Jakarta Bean Validation Responsibility](#bean-validation-boundary)
- [SpringValidatorAdapter](#spring-validator-adapter)
- [LocalValidatorFactoryBean](#local-validator-factory-bean)
- [Validation Groups and Spring Hints](#validation-groups-hints)
- [Executable Method Validation](#method-validation-model)
- [MethodValidator, MethodValidationAdapter, and Result Model](#method-validation-adapter)
- [MethodValidationPostProcessor and the AOP Boundary](#method-validation-post-processor)

## <a id="bean-validation-boundary">Spring vs Jakarta Bean Validation Responsibility</a>

<details>
<summary>Click for details</summary>

Jakarta Bean Validation defines a separate validation specification: constraint annotations such as `@NotNull`, the `jakarta.validation.Validator` contract, groups, cascading with `@Valid`, executable validation, and provider behavior. Spring does not redefine those semantics. Its job is to integrate a Bean Validation provider with Spring's own `Validator`, `Errors`, `DataBinder`, bean lifecycle, and method-validation infrastructure.

```java
record RegistrationCommand(
    @jakarta.validation.constraints.NotBlank String username,
    @jakarta.validation.constraints.Min(18) int age) {
}
```

The annotations above belong to Jakarta Bean Validation. What Spring adds is the ability to run the provider through Spring infrastructure and translate violations into Spring-friendly error representations when appropriate.

Keep the ownership line clear when reasoning about behavior. Whether `@Min` accepts a given value, how cascaded constraints are evaluated, or how a provider interprets custom constraints belongs to the Bean Validation specification/provider. How those results enter `Errors`, a `DataBinder`, or Spring method validation belongs here.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-validator-adapter">SpringValidatorAdapter</a>

<details>
<summary>Click for details</summary>

`SpringValidatorAdapter` is the bridge between the Jakarta Bean Validation API and Spring's validation model. It wraps a `jakarta.validation.Validator`, implements Spring's `SmartValidator`, and also exposes the Jakarta `Validator` operations.

When used through Spring's `validate(...)` methods, it asks the Bean Validation provider for `ConstraintViolation`s and adapts them into Spring `Errors`. Property violations become field-oriented errors when Spring can resolve a field path; object-level violations remain object errors. The adapted errors retain codes, arguments, rejected values, and provider information needed by later layers.

```java
jakarta.validation.Validator jakartaValidator = validatorFactory.getValidator();
SpringValidatorAdapter springValidator =
    new SpringValidatorAdapter(jakartaValidator);

BeanPropertyBindingResult errors =
    new BeanPropertyBindingResult(command, "command");
springValidator.validate(command, errors);
```

This adapter is useful when application infrastructure already speaks Spring `Validator`/`Errors` but the rules are declared with Jakarta constraint annotations. It is an integration layer, not a second implementation of the constraint specification.

### References

- Spring Framework 6.1.14 API — `SpringValidatorAdapter`

</details>

- [Back to top](#back-to-top)

---

## <a id="local-validator-factory-bean">LocalValidatorFactoryBean</a>

<details>
<summary>Click for details</summary>

`LocalValidatorFactoryBean` is Spring's central ApplicationContext-friendly bootstrap for Jakarta Bean Validation. It builds the underlying `ValidatorFactory` and exposes the default validator through three useful views: Spring `Validator`/`SmartValidator`, Jakarta `Validator`, and Jakarta `ValidatorFactory`.

```java
@Bean
LocalValidatorFactoryBean validator() {
    return new LocalValidatorFactoryBean();
}
```

That dual role means the same configured provider can participate in `DataBinder` validation and also be injected where code needs the native Jakarta API. Spring can additionally configure provider class, XML mappings, parameter-name discovery, validation properties, message interpolation, and the `ConstraintValidatorFactory`.

By default, Spring uses a `SpringConstraintValidatorFactory`, allowing constraint validator instances to be created through the containing Spring `BeanFactory`. This makes dependency injection into custom `ConstraintValidator` implementations possible without changing Bean Validation semantics.

`setValidationMessageSource(...)` can connect provider message interpolation to a Spring `MessageSource`. The integration point lives here, while `MessageSource` configuration and general i18n behavior remain owned by Core Container.

### References

- Spring Framework 6.1.14 API — `LocalValidatorFactoryBean`

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-groups-hints">Validation Groups and Spring Hints</a>

<details>
<summary>Click for details</summary>

Bean Validation groups let one constrained type participate in different validation scenarios. Spring connects that model to `SmartValidator` hints: when a Bean Validation-backed Spring validator receives `Class<?>` hints, those classes can be used as validation groups.

```java
interface Create {}
interface Update {}

DataBinder binder = new DataBinder(command, "command");
binder.addValidators(localValidatorFactoryBean);
binder.validate(Create.class);
```

For Spring-driven method validation, `@Validated` can declare groups at the type level. In Spring 6.1's method-validation infrastructure, group determination can also inspect applicable `@Validated` metadata for the invoked method/type according to the method validator's rules.

For proxy-based validation through `MethodValidationPostProcessor`, keep activation separate from group selection: the target class still needs a type-level `@Validated` annotation (or the configured equivalent) to match the validation pointcut. A method-level `@Validated` can override the validation groups for that method, but by itself it does not activate proxy-based method validation for the bean.

Groups should describe genuinely different validation scenarios, not become a substitute for clear input types. If create and update workflows expose substantially different data, separate command models may be simpler than a large matrix of groups.

Also distinguish **groups** from `@Valid`: `@Valid` requests cascaded validation of associated objects, while groups select which constraints participate in a validation operation.

</details>

- [Back to top](#back-to-top)

---

## <a id="method-validation-model">Executable Method Validation</a>

<details>
<summary>Click for details</summary>

Object validation asks whether one object satisfies its constraints. Executable method validation asks whether a **method invocation boundary** satisfies constraints on parameters and return values. Bean Validation defines the executable-validation semantics; Spring 6.1 adds a richer framework-level result model around them.

```java
public interface PricingService {
    @jakarta.validation.constraints.Positive
    BigDecimal quote(
        @jakarta.validation.constraints.NotBlank String sku,
        @jakarta.validation.constraints.Positive int quantity);
}
```

Parameter constraints are checked against the arguments about to be passed to the method. Return-value constraints are checked against the produced result. Cascaded validation can validate object parameters or return values when Jakarta `@Valid` is present.

This is different from putting a `Validator` inside the method body. Method validation treats the callable boundary itself as the validation target and can be applied consistently by infrastructure. It is also different from MVC/WebFlux controller argument binding; those web-specific lifecycles remain in their owning modules even when they reuse the same validation contracts.

</details>

- [Back to top](#back-to-top)

---

## <a id="method-validation-adapter">MethodValidator, MethodValidationAdapter, and Result Model</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 introduced the `MethodValidator` abstraction and `MethodValidationAdapter` to expose method-validation results in Spring terms instead of forcing every caller to work directly with raw `ConstraintViolation` sets.

`MethodValidationAdapter` uses a Jakarta Bean Validation `Validator` underneath. Its `validateArguments(...)` and `validateReturnValue(...)` operations produce a `MethodValidationResult`. Results are organized by method parameter or return value, with Spring `MessageSourceResolvable` error information; cascaded object violations can be represented through parameter error structures that also expose binding-style errors.

```java
MethodValidationAdapter adapter = new MethodValidationAdapter(jakartaValidator);
Class<?>[] groups = adapter.determineValidationGroups(target, method);

MethodValidationResult result = adapter.validateArguments(
    target, method, parameters, arguments, groups);

if (result.hasErrors()) {
    result.getAllValidationResults()
        .forEach(System.out::println);
}
```

The adapter can use a `MessageCodesResolver` and a `ParameterNameDiscoverer` so violations receive stable Spring-style codes and meaningful parameter identities. Group selection is determined separately from validation, which keeps the result model independent from how validation was triggered.

Use this model when infrastructure needs structured method-validation evidence. Ordinary application code more often lets higher-level Spring infrastructure apply validation and handle failures.

</details>

- [Back to top](#back-to-top)

---

## <a id="method-validation-post-processor">MethodValidationPostProcessor and the AOP Boundary</a>

<details>
<summary>Click for details</summary>

`MethodValidationPostProcessor` is Spring's convenient container-level wiring for proxy-based method validation. It detects eligible beans, by default through Spring's type-level `@Validated`, and installs method-validation advice that delegates to a Jakarta Bean Validation provider.

```java
@Configuration
class ValidationConfig {
    @Bean
    static MethodValidationPostProcessor methodValidationPostProcessor() {
        MethodValidationPostProcessor processor =
            new MethodValidationPostProcessor();
        processor.setAdaptConstraintViolations(true);
        return processor;
    }
}

@Validated
class PricingServiceImpl implements PricingService {
    // constrained methods
}
```

In Spring Framework 6.1.14, the default failure mode is a Jakarta `ConstraintViolationException`. When `setAdaptConstraintViolations(true)` is enabled, violations are adapted to Spring's `MethodValidationResult` model and raised through `MethodValidationException` instead.

The important boundary is **wiring versus proxy mechanics**. This chapter explains why the post-processor is used, which beans are eligible, group selection, and what validation result/exception model is produced. How Spring proxies intercept calls, advisor ordering, proxy type, and self-invocation behavior belong to the Aspect module.

### References

- Spring Framework 6.1.14 API — `MethodValidationPostProcessor`

</details>

- [Back to top](#back-to-top)
