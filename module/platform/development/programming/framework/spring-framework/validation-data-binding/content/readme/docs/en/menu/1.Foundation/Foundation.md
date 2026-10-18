<a id="back-to-top"></a>

# Validation, Binding, Conversion, and Formatting

## Menu
- [Why These Mechanisms Exist](#validation-binding-purpose)
- [Validation vs Data Binding](#validation-vs-binding)
- [Conversion vs Formatting](#conversion-vs-formatting)
- [Module Boundary and Neighboring Owners](#validation-binding-module-boundary)

## <a id="validation-binding-purpose">Why These Mechanisms Exist</a>

<details>
<summary>Click for details</summary>

Applications rarely receive values in the exact shape their domain objects need. External input often arrives as text, loosely typed maps, configuration values, form fields, command-line values, or serialized data. The target object, however, expects typed state with invariants: an `int`, a `LocalDate`, a value object, an enum, or a nested object graph.

Spring separates this work into several cooperating mechanisms because each solves a different problem:

- **data binding** moves named input values into an object model;
- **type conversion** changes a value from one Java type to another;
- **formatting** parses and prints values with presentation context such as `Locale`;
- **validation** checks whether the resulting object or value satisfies rules.

Keeping these concerns separate makes the same conversion or validation rule reusable outside one transport. A `Converter<String, OrderId>` can be useful in binding, configuration, or infrastructure code. A `Validator` can validate the same object whether the caller is a web adapter, a batch job, or plain application code.

Think of the overall flow as a pipeline rather than one "magic bind" operation:

```text
raw values
   ↓
binding selects the target location
   ↓
conversion / formatting during binding
   ↓
assignment produces typed object state
   ↓
validation
   ↓
structured errors or accepted state
```

The exact orchestration is supplied later by `DataBinder`, but the mental model starts here: converting a value is not the same as deciding where to write it, and successfully writing it is not the same as proving that the resulting object is valid.

### References

- [Spring Framework 6.1.14 `DataBinder`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/validation/DataBinder.html)
- [Spring Framework 6.1.14 `ConversionService`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/ConversionService.html)
- [Spring Framework 6.1.14 `Validator`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/validation/Validator.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-vs-binding">Validation vs Data Binding</a>

<details>
<summary>Click for details</summary>

**Binding** answers "which input value should be applied to which target location?" **Validation** answers "is the resulting value or object acceptable?" Mixing those questions leads to fragile code because a security or shape decision gets confused with a business rule.

Suppose an input contains `age = "abc"`. If the target property is an `int`, the first problem is binding/conversion: Spring cannot produce an integer value. No business validator is required to discover that mismatch. By contrast, `age = "15"` can convert successfully but may still fail a rule such as "customer age must be at least 18". That second failure belongs to validation.

```java
public final class Registration {
    private int age;

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}

public final class RegistrationValidator implements Validator {
    @Override
    public boolean supports(Class<?> type) {
        return Registration.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Registration registration = (Registration) target;
        if (registration.getAge() < 18) {
            errors.rejectValue("age", "age.tooYoung");
        }
    }
}
```

The distinction also matters for safe input handling. Validation must not be used as a substitute for controlling which properties may be bound. A field that should never be writable from external input should be excluded from the binding surface even if a validator could later reject some values.

Practical rule: diagnose failures in the stage where they originate. Type mismatch, missing required bind value, or inaccessible property are binding concerns; domain acceptability is a validation concern.

</details>

- [Back to top](#back-to-top)

---

## <a id="conversion-vs-formatting">Conversion vs Formatting</a>

<details>
<summary>Click for details</summary>

General conversion and formatting both transform values, but they optimize for different contexts.

`ConversionService` models reusable type-to-type conversion. A converter typically answers a structural question such as "how does a `String` become an `OrderId`?" or "how does one enum family map to another representation?" The conversion contract does not inherently mean "display this for a user".

Formatting is presentation-oriented. `Printer<T>` turns a typed value into text for display, while `Parser<T>` turns text back into a typed value. Both receive a `Locale`, and `Formatter<T>` combines the two contracts. That makes formatting a better fit when the textual representation depends on user-facing conventions such as decimal separators, date patterns, currency style, or annotation-specific display rules.

```java
ConversionService conversionService = new DefaultConversionService();
Integer quantity = conversionService.convert("12", Integer.class);

Formatter<BigDecimal> amountFormatter = new Formatter<>() {
    @Override
    public BigDecimal parse(String text, Locale locale) throws ParseException {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
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
            throw new ParseException("Invalid number: " + text, errorOffset);
        }
        return decimal;
    }

    @Override
    public String print(BigDecimal value, Locale locale) {
        return NumberFormat.getNumberInstance(locale).format(value);
    }
};
```

The first operation is about the target Java type. The second explicitly models how text should be interpreted and displayed for a locale.

Use a plain converter when the transformation has one stable semantic meaning independent of presentation. Use a formatter when text representation is part of the problem. Avoid putting locale-sensitive parsing into a global `Converter<String, T>` because the converter API has no locale argument and the resulting policy becomes ambiguous.

### References

- [Spring Framework 6.1.14 `Formatter`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Formatter.html)
- [Spring Framework 6.1.14 `Printer`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Printer.html)
- [Spring Framework 6.1.14 `Parser`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Parser.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-binding-module-boundary">Module Boundary and Neighboring Owners</a>

<details>
<summary>Click for details</summary>

This module owns Spring Framework's reusable validation, binding, conversion, and formatting contracts. It intentionally stops before transport-specific controller lifecycles.

The main ownership boundaries are:

- **Spring Core Container** supplies shared infrastructure such as `ConversionService` registration in an application context and owns `MessageSource`/i18n as a broader container concern.
- **this module** explains `BeanWrapper`, `ConversionService`, converter SPIs, formatter SPIs, `Errors`/`BindingResult`, `Validator`, `DataBinder`, and Spring's integration with Jakarta Bean Validation.
- **Spring MVC** owns `WebDataBinder`, `@InitBinder`, request parameter/model-attribute binding, controller argument validation, and the Servlet request lifecycle.
- **Spring WebFlux** owns the corresponding reactive controller-binding and validation lifecycle.

That boundary keeps the core mental model reusable. For example, `DataBinder` is not a web-only type; web binders build on it. Likewise, a `FormatterRegistry` is generic formatting infrastructure even though MVC and WebFlux can both consume one.

When reading later chapters, ask two questions whenever an API appears:

1. Is this the reusable core contract that transforms, binds, or validates values?
2. Or is it an adapter that decides how an HTTP/controller lifecycle invokes that contract?

The first belongs here. The second is handed off to the web or reactive module. This prevents examples from accidentally teaching controller-specific behavior as if it were a property of the underlying Spring core APIs.

The same discipline applies to Jakarta Bean Validation. This module teaches how Spring adapts that specification into its `Validator` ecosystem; provider-specific constraint behavior remains the responsibility of the Bean Validation specification/provider itself.

</details>

- [Back to top](#back-to-top)
