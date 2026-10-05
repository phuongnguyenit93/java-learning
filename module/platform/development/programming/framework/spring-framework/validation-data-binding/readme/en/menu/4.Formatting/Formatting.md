<a id="back-to-top"></a>

# Field Formatting

## Menu
- [Why Formatting Is Separate from General Conversion](#formatting-purpose)
- [Printer and Parser Contracts](#printer-parser-contract)
- [Formatter as Parse and Print](#formatter-contract)
- [FormatterRegistry and Central Registration](#formatter-registry)
- [Annotation-Driven Field Formatting](#annotation-formatting)
- [FormattingConversionService](#formatting-conversion-service)
- [Locale-Sensitive Formatting Boundary](#formatting-locale-boundary)

## <a id="formatting-purpose">Why Formatting Is Separate from General Conversion</a>

<details>
<summary>Click for details</summary>

General conversion answers a type-system question: "how can value A become value B?" Formatting answers a presentation question: "how should a value be represented as text for this user or field, and how should that text be parsed back?"

That difference matters whenever textual representation depends on context. The number `1234.5` may be displayed as `1,234.5` in one locale and `1.234,5` in another. A date may use a field-specific annotation or application-wide presentation rule. Those are not merely `String -> T` conversions; they are text presentation policies.

Spring therefore models formatting with `Printer`, `Parser`, and `Formatter` while integrating them with the same conversion infrastructure used by `ConversionService`.

```text
typed value
   ↓ Printer<T> + Locale
display text

input text
   ↓ Parser<T> + Locale
typed value
```

This separation keeps domain conversion clean. For example, a `Converter<String, OrderId>` can define one stable textual identifier syntax, while a money or date formatter can honor locale-sensitive presentation rules.

Formatting is also field-oriented: the same Java type may be presented differently depending on an annotation or registration. That is why Spring's formatting layer works with field metadata and `TypeDescriptor`, rather than treating every `String <-> T` transformation as globally identical.

Use formatting when text presentation itself is part of the requirement. Use general conversion for context-independent type transformation. Keeping that boundary clear prevents locale and display concerns from leaking into reusable converter logic.

</details>

- [Back to top](#back-to-top)

---

## <a id="printer-parser-contract">Printer and Parser Contracts</a>

<details>
<summary>Click for details</summary>

`Printer<T>` and `Parser<T>` split formatting into two directional contracts.

- `Printer<T>.print(T, Locale)` produces display text from a typed object.
- `Parser<T>.parse(String, Locale)` produces a typed object from text.

Both receive the current `Locale`, which is the key difference from a plain converter.

```java
public final class PercentagePrinter implements Printer<BigDecimal> {
    @Override
    public String print(BigDecimal value, Locale locale) {
        NumberFormat format = NumberFormat.getPercentInstance(locale);
        return format.format(value);
    }
}

public final class PercentageParser implements Parser<BigDecimal> {
    @Override
    public BigDecimal parse(String text, Locale locale) throws ParseException {
        NumberFormat numberFormat = NumberFormat.getPercentInstance(locale);
        if (!(numberFormat instanceof DecimalFormat format)) {
            throw new ParseException("No DecimalFormat for locale: " + locale, 0);
        }
        format.setParseBigDecimal(true);
        ParsePosition position = new ParsePosition(0);
        Number number = format.parse(text, position);
        if (!(number instanceof BigDecimal decimal)
                || position.getIndex() != text.length()) {
            int errorOffset = position.getErrorIndex() >= 0
                    ? position.getErrorIndex()
                    : position.getIndex();
            throw new ParseException("Invalid percentage: " + text, errorOffset);
        }
        return decimal;
    }
}
```

The two contracts can be registered separately when an application truly needs only one direction, but most field-editing scenarios need both. In that case `Formatter<T>` is the simpler abstraction.

Parsing should reject malformed text instead of silently inventing a value. The `Parser` contract allows `ParseException` and `IllegalArgumentException`; downstream binding can then represent that failure as a structured field error.

Printing should be deterministic for the supplied value and locale. Avoid request-specific mutable state in formatter objects because formatting services are commonly shared infrastructure.

### References

- [Spring Framework 6.1.14 `Printer`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Printer.html)
- [Spring Framework 6.1.14 `Parser`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Parser.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="formatter-contract">Formatter as Parse and Print</a>

<details>
<summary>Click for details</summary>

`Formatter<T>` combines `Printer<T>` and `Parser<T>` for one object type. It is the usual choice when the same presentation policy must support both displaying a value and accepting textual input.

```java
public final class LocalDateFormatter implements Formatter<LocalDate> {
    private static final DateTimeFormatter PATTERN =
            DateTimeFormatter.ofPattern("dd MMM uuuu");

    @Override
    public LocalDate parse(String text, Locale locale) {
        return LocalDate.parse(text, PATTERN.withLocale(locale));
    }

    @Override
    public String print(LocalDate value, Locale locale) {
        return PATTERN.withLocale(locale).format(value);
    }
}
```

The formatter should represent one coherent textual contract. If `print` emits a representation that `parse` cannot reasonably consume, round-trip behavior becomes surprising for editable fields.

That does not mean every formatted value must be perfectly reversible. Display-only rounding or human-friendly formatting can intentionally lose information. The important requirement is to make the policy explicit and choose a separate printer/parser when asymmetric behavior is clearer.

`Formatter` belongs at the presentation boundary of this module. It can be consumed by binding infrastructure, but it does not decide how a controller obtains input or selects locale. MVC/WebFlux own those transport-specific lifecycles.

### References

- [Spring Framework 6.1.14 `Formatter`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Formatter.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="formatter-registry">FormatterRegistry and Central Registration</a>

<details>
<summary>Click for details</summary>

`FormatterRegistry` is the configuration side of Spring's formatting system. It extends `ConverterRegistry`, so one registry can hold both general converters and field-formatting rules.

The registry supports several scopes:

- register a formatter whose generic type determines the field type;
- register a formatter explicitly for one field type;
- register separate printer/parser instances;
- register an `AnnotationFormatterFactory` for annotation-driven formatting.

```java
FormattingConversionService service = new FormattingConversionService();

service.addFormatter(new LocalDateFormatter());
service.addFormatterForFieldType(
        BigDecimal.class,
        new NumberStyleFormatter()
);
```

Central registration makes formatting policy consistent across all consumers of that service. It also means broad registrations have wide impact. A global `Formatter<BigDecimal>` affects every matching field unless a more specific annotation-driven rule applies, so global rules should represent application-wide policy rather than a local screen preference.

Field annotation registration is the escape hatch for context that belongs to one field or category of fields. That lets the default policy stay simple while explicit annotations opt into special formatting.

Configuration code should mutate the registry during application setup, then application components should consume it as a `ConversionService`. This mirrors the separation between `ConverterRegistry` and `ConversionService` in the core conversion system.

### References

- [Spring Framework 6.1.14 `FormatterRegistry`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/FormatterRegistry.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="annotation-formatting">Annotation-Driven Field Formatting</a>

<details>
<summary>Click for details</summary>

Sometimes the Java type alone is not enough to choose a formatting rule. Two `LocalDate` fields may require different patterns, or one numeric field may be a percentage while another is plain decimal. Annotation-driven formatting attaches that intent directly to the field metadata.

Spring models this with `AnnotationFormatterFactory<A>`. A factory declares which field types an annotation supports and returns the appropriate printer and parser for the concrete annotation instance.

```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface YearMonthText {
    String pattern() default "MM/uuuu";
}

public final class YearMonthTextFactory
        implements AnnotationFormatterFactory<YearMonthText> {

    @Override
    public Set<Class<?>> getFieldTypes() {
        return Set.of(YearMonth.class);
    }

    @Override
    public Printer<?> getPrinter(YearMonthText ann, Class<?> fieldType) {
        return (Printer<YearMonth>) (value, locale) ->
                DateTimeFormatter.ofPattern(ann.pattern(), locale).format(value);
    }

    @Override
    public Parser<?> getParser(YearMonthText ann, Class<?> fieldType) {
        return (Parser<YearMonth>) (text, locale) ->
                YearMonth.parse(text, DateTimeFormatter.ofPattern(ann.pattern(), locale));
    }
}
```

The factory's `getFieldTypes()` limits where the annotation is valid. Spring can also perform a coercion when a returned printer/parser type is compatible through the conversion system, but the clearest design is to keep annotation and field types aligned directly.

Use annotations for declarative, field-specific presentation policy. Avoid annotations that hide business validation or transport behavior; formatting annotations should describe textual representation, not domain acceptance rules.

### References

- [Spring Framework 6.1.14 `AnnotationFormatterFactory`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/AnnotationFormatterFactory.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="formatting-conversion-service">FormattingConversionService</a>

<details>
<summary>Click for details</summary>

`FormattingConversionService` is where Spring's conversion and formatting models meet. It extends `GenericConversionService` and implements `FormatterRegistry`, so the same object can register converters/formatters and serve conversion requests.

At runtime, formatters are adapted into the conversion system. This lets callers continue using the familiar `ConversionService` API while field metadata and locale-sensitive formatting rules participate behind the scenes.

```java
DefaultFormattingConversionService service =
        new DefaultFormattingConversionService();

String display = service.convert(
        LocalDate.of(2026, 10, 4),
        String.class
);
```

`DefaultFormattingConversionService` adds the default converters plus a standard set of formatters suitable for common environments. In Spring Framework 6.1.14 that includes number formatting and Java time formatting when the corresponding APIs are available. Applications can add or override policies by registering their own converters, formatters, or annotation formatter factories.

The practical advantage is one coherent infrastructure service for type transformation and field presentation. The risk is also centralization: a broad formatter registration can affect many binding sites. Keep global defaults unsurprising and use field annotations for exceptions.

Do not assume that `DefaultFormattingConversionService` knows application-specific formats. Built-in defaults provide useful baseline behavior; business-specific value objects and custom textual contracts still need explicit registration.

### References

- [Spring Framework 6.1.14 `FormattingConversionService`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/support/FormattingConversionService.html)
- [Spring Framework 6.1.14 `DefaultFormattingConversionService`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/support/DefaultFormattingConversionService.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="formatting-locale-boundary">Locale-Sensitive Formatting Boundary</a>

<details>
<summary>Click for details</summary>

Locale-sensitive formatting is about *text representation*, not general internationalization ownership. `Printer` and `Parser` receive a `Locale` so the same formatter can apply conventions such as decimal separators, grouping, month names, or currency display without hard-coding one culture.

```java
NumberStyleFormatter formatter = new NumberStyleFormatter();

String us = formatter.print(new BigDecimal("1234.50"), Locale.US);
String de = formatter.print(new BigDecimal("1234.50"), Locale.GERMANY);
```

The formatter does not decide where the locale came from. A standalone caller can supply one directly; a web framework can resolve it from its own request context. Locale resolution inside MVC/WebFlux is therefore outside this module's controller-binding scope.

Formatting is also different from `MessageSource` localization. A formatter converts a typed value to/from locale-aware text. `MessageSource` resolves message codes into localized messages. A validation error might therefore use both systems in sequence: formatting can render a rejected value while `MessageSource` later resolves the error message.

Common pitfalls include:

- using the JVM default locale implicitly and getting environment-dependent behavior;
- treating a locale-formatted decimal as a stable machine serialization format;
- assuming parsing rules are identical across locales;
- embedding translated validation messages inside formatter code.

For durable machine-to-machine formats, prefer explicit locale-independent representations. Use Spring formatting where the text is meant for human-facing input or display.

</details>

- [Back to top](#back-to-top)
