<a id="back-to-top"></a>

# Spring Type Conversion

## Menu
- [ConversionService as the Runtime Entry Point](#conversion-service-role)
- [ConverterRegistry and Converter Registration](#converter-registry)
- [Converter for One Source-to-Target Pair](#converter-contract)
- [ConverterFactory for a Target Type Family](#converter-factory-contract)
- [GenericConverter and Conditional Conversion](#generic-conditional-conversion)
- [TypeDescriptor and Conversion Context](#type-descriptor-context)
- [Converter Selection and Conversion Failures](#conversion-failure-model)
- [Legacy PropertyEditor Integration](#property-editor-boundary)

## <a id="conversion-service-role">ConversionService as the Runtime Entry Point</a>

<details>
<summary>Click for details</summary>

`ConversionService` is the consumer-facing entry point into Spring's conversion system. Callers ask whether a source type can be converted and then request the conversion; they do not need to know which converter implementation performs the work.

```java
ConversionService service = DefaultConversionService.getSharedInstance();

boolean supported = service.canConvert(String.class, Integer.class);
Integer quantity = service.convert("42", Integer.class);
```

This indirection is valuable because binding code can depend on one stable service while converter registrations evolve independently. The same service can choose built-in converters, application converters, factories, or generic/conditional converters according to the source and target descriptors.

The richer overloads accept `TypeDescriptor`. Those overloads preserve context that raw `Class<?>` values cannot express, such as collection element types, map key/value types, annotations, and property location. That context becomes important for collection conversion and annotation-aware logic.

`canConvert(...)` is a capability query, not a proof that every future value will succeed. For collections, arrays, and maps, Spring can report that a structural conversion path exists while an actual element later fails to convert. Callers that perform conversion on untrusted or variable data must still handle `ConversionException`.

Treat `ConversionService` as application infrastructure: configure reusable converters once, then let consumers ask for conversions. Avoid scattering `Integer.parseInt`, date parsing, or value-object constructors through binding code when those transformations represent shared application policy.

### References

- [Spring Framework 6.1.14 `ConversionService`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/ConversionService.html)
- [Spring Framework 6.1.14 `DefaultConversionService`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/support/DefaultConversionService.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="converter-registry">ConverterRegistry and Converter Registration</a>

<details>
<summary>Click for details</summary>

`ConversionService` is about *using* conversion; `ConverterRegistry` is about *configuring* the conversion system. Keeping those roles separate prevents application components from mutating conversion policy merely because they need to convert a value.

The registry accepts several converter shapes:

```java
DefaultConversionService service = new DefaultConversionService();

service.addConverter(new StringToOrderIdConverter());
service.addConverterFactory(new StringToCodeEnumFactory());
service.addConverter(new EntityReferenceConverter());
```

When a plain `Converter<S,T>` or `ConverterFactory<S,R>` is registered, Spring normally derives the convertible source/target types from its generic parameters. The registry also has an overload that accepts source and target `Class` values explicitly, which is useful when the same converter instance is intentionally reused for several pairs.

Registration order should not become an accidental business rule. Prefer converter contracts that clearly identify when they apply. When multiple candidates can conceptually match, use conditional conversion or narrower source/target pairs so selection depends on explicit metadata rather than fragile assumptions.

Central registration also gives one place to review global conversion policy. A converter registered broadly can affect binding and other framework facilities that use the same service, so converters should be deterministic, thread-safe where required by their contract, and free of request-specific state.

### References

- [Spring Framework 6.1.14 `ConverterRegistry`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/converter/ConverterRegistry.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="converter-contract">Converter for One Source-to-Target Pair</a>

<details>
<summary>Click for details</summary>

Use `Converter<S,T>` when one source type has one well-defined transformation to one target type. It is the simplest conversion SPI and is a functional interface.

```java
public record OrderId(long value) {}

public final class StringToOrderIdConverter
        implements Converter<String, OrderId> {

    @Override
    public OrderId convert(String source) {
        long value = Long.parseLong(source.trim());
        if (value <= 0) {
            throw new IllegalArgumentException("Order id must be positive");
        }
        return new OrderId(value);
    }
}
```

Spring's `Converter` contract guarantees that the `source` argument passed to `convert` is non-null. A converter may return `null`, although doing so should have a clear semantic meaning and should not hide invalid input.

Converters registered in a conversion system are expected to be thread-safe and shareable. Do not store per-conversion mutable state in instance fields. Parse from the method argument and return a result.

Throw `IllegalArgumentException` when a non-null source cannot be converted according to the contract. That failure can later surface as a conversion/binding error when a binder invokes the service.

Choose a plain converter when the transformation is stable and context-free. If the operation needs the target subtype, source/target field annotations, generic element types, or a conditional match, another SPI is a better fit.

### References

- [Spring Framework 6.1.14 `Converter`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/converter/Converter.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="converter-factory-contract">ConverterFactory for a Target Type Family</a>

<details>
<summary>Click for details</summary>

`ConverterFactory<S,R>` is useful when one source representation can convert into several related target subtypes. Instead of registering a separate converter class for every subtype, the factory receives the requested target `Class<T>` and returns the appropriate `Converter<S,T>`.

A common mental model is "one source format, one target family":

```java
public final class StringToEnumFactory
        implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return source -> Enum.valueOf(targetType, source.trim());
    }
}
```

If the requested target is `Priority.class`, the factory produces a `String -> Priority` converter; if it is `Status.class`, it produces `String -> Status`. The base target type `R` expresses the range that the factory supports.

This is better than a `GenericConverter` when the only varying information is the requested target subtype and the algorithm is otherwise uniform. It keeps the API strongly typed and the implementation easy to test.

Do not use a converter factory to hide unrelated target types under a common `Object` range. The target family should have a meaningful common base and a coherent conversion policy. If the decision depends on annotations, generics, or other descriptor metadata, prefer a conditional/generic converter instead.

### References

- [Spring Framework 6.1.14 `ConverterFactory`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/converter/ConverterFactory.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="generic-conditional-conversion">GenericConverter and Conditional Conversion</a>

<details>
<summary>Click for details</summary>

`GenericConverter` is the most flexible conversion SPI. It can advertise several source/target pairs and its `convert` method receives both source and target `TypeDescriptor`s. That extra context is the reason to use it; otherwise a simpler `Converter` is easier to understand.

```java
public final class EntityReferenceConverter
        implements ConditionalGenericConverter {

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return null; // Consider all pairs; matches(...) narrows the real scope.
    }

    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        return sourceType.getType() == String.class
                && targetType.hasAnnotation(EntityRef.class);
    }

    @Override
    public Object convert(Object source,
                          TypeDescriptor sourceType,
                          TypeDescriptor targetType) {
        // Look up or construct the application-specific reference.
        return resolveReference((String) source, targetType.getType());
    }
}
```

`ConditionalGenericConverter` combines the generic converter with a `matches(...)` condition. The conversion service can first consider a source/target pair, then let the converter decide whether descriptor context makes it applicable. Real Spring converters use this pattern for metadata-sensitive conversions.

Unlike `Converter`, a `GenericConverter` can receive a null source. Its implementation therefore needs an explicit null policy when relevant.

Avoid reaching for this SPI only because it is powerful. Broad convertible pairs plus weak matching rules can make the global conversion system hard to reason about. Prefer the narrowest SPI that expresses the actual decision.

### References

- [Spring Framework 6.1.14 `GenericConverter`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/converter/GenericConverter.html)
- [Spring Framework 6.1.14 `ConditionalGenericConverter`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/converter/ConditionalGenericConverter.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="type-descriptor-context">TypeDescriptor and Conversion Context</a>

<details>
<summary>Click for details</summary>

A Java `Class<?>` tells you the raw runtime type, but conversion often needs more. `TypeDescriptor` carries the type together with location and nested-type context. It can describe a field, property, or method parameter; expose annotations; and describe collection elements or map keys and values.

Consider two fields with the same raw type:

```java
class PriceForm {
    @CurrencyCode("USD")
    BigDecimal retailPrice;

    @CurrencyCode("EUR")
    BigDecimal wholesalePrice;
}
```

Both fields are `BigDecimal.class`, so raw classes cannot explain why their textual interpretation might differ. A `TypeDescriptor` can preserve the field annotations and let a conditional converter or formatter make a contextual decision.

It also matters for containers. `List<Integer>` and `List<UUID>` both erase to `List.class`, but their element descriptors express different conversion requirements. Spring can use that nested metadata when converting collection contents.

```java
TypeDescriptor source = TypeDescriptor.valueOf(String.class);
TypeDescriptor target = TypeDescriptor.collection(
        List.class,
        TypeDescriptor.valueOf(Integer.class)
);

Object converted = conversionService.convert(
        List.of("1", "2", "3"),
        TypeDescriptor.collection(List.class, source),
        target
);
```

The rule is simple: use class-based conversion for straightforward scalar transformations; use descriptor-aware conversion when generics, annotations, or the property location are part of the semantics.

### References

- [Spring Framework 6.1.14 `TypeDescriptor`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/TypeDescriptor.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="conversion-failure-model">Converter Selection and Conversion Failures</a>

<details>
<summary>Click for details</summary>

Converter selection is a two-part problem: first Spring must find an eligible conversion path; then the chosen converter must successfully handle the actual value.

If no converter is available for a requested source/target pair, conversion fails because the system has no transformation strategy. If a converter exists but rejects the concrete value, the failure occurs during conversion itself. Those cases are different from validation: conversion asks whether a typed target value can be produced, while validation asks whether that value is acceptable to the application.

```java
try {
    UUID id = conversionService.convert(rawId, UUID.class);
    // use typed id
}
catch (ConversionException ex) {
    // The source could not be converted using the configured system.
}
```

`canConvert(...)` is useful when conversion is optional or when code needs to select a fallback strategy, but it should not become a preflight call before every `convert`. For many cases the conversion itself is the authoritative operation. In collection/map conversion, `canConvert` may be true even if a later element causes `ConversionException`.

Common pitfalls include registering overly broad converters, performing locale-sensitive parsing in context-free converters, and swallowing malformed input by returning arbitrary defaults. A converter should preserve the distinction between "no value", "invalid value", and a legitimate domain value.

When conversion is invoked through a binder, Spring can translate property-access/conversion failures into structured binding errors. The detailed error orchestration belongs to the DataBinder chapter; the conversion layer's responsibility is to produce a target value or fail clearly.

</details>

- [Back to top](#back-to-top)

---

## <a id="property-editor-boundary">Legacy PropertyEditor Integration</a>

<details>
<summary>Click for details</summary>

Spring predates `ConversionService`, so it still supports the JavaBeans `PropertyEditor` model. Property editors are stateful editor objects that can convert text to a property value and back. They remain important for compatibility and for some low-level binding extension points, but they are not the preferred general-purpose conversion abstraction for new shared policies.

The main design difference is scope and state. A Spring `Converter` is a shareable, thread-safe strategy registered centrally. A `PropertyEditor` traditionally carries mutable editor state such as its current value, so editor instances must be managed with appropriate scope rather than treated as stateless global converters.

`DataBinder` can still register custom editors:

```java
DataBinder binder = new DataBinder(target);
binder.registerCustomEditor(
        Date.class,
        new CustomDateEditor(new SimpleDateFormat("yyyy-MM-dd"), true)
);
```

For modern application-level conversion, prefer a `ConversionService` with converters and formatters. Keep `PropertyEditor` knowledge because existing Spring APIs and legacy integrations still expose it, and because it explains some binding extension points.

Do not combine both mechanisms without a reason. If one application concept has a global `Converter` and a field-specific custom editor with different semantics, the effective policy becomes difficult to predict and review. Use the narrowest mechanism that matches the intended scope.

</details>

- [Back to top](#back-to-top)
