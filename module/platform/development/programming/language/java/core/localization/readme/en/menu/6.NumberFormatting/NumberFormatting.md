# Number Formatting

The value `1234567.89` has one numeric meaning, but users in different locales may expect different decimal and grouping symbols. **A number and its displayed text are different representations.**

## <a id="number-format">Locale-Sensitive NumberFormat</a>

More precisely, `NumberFormat` is an **abstraction for converting between numeric values and human-readable number text** according to a locale.

```text
format
number → localized String

parse
localized String → Number
```

It exists because decimal separators, grouping, percent signs, currency presentation, and some digit conventions vary across locales.

The main pieces in this API family are:

```text
NumberFormat
→ common abstraction/factories

DecimalFormat
→ implementation with detailed pattern control

DecimalFormatSymbols
→ locale-sensitive symbols

Locale
→ context used to choose conventions
```

`NumberFormat` creates formatters for a locale:

```java
BigDecimal value = new BigDecimal("1234567.89");

NumberFormat vi = NumberFormat.getNumberInstance(
        Locale.forLanguageTag("vi-VN")
);
NumberFormat us = NumberFormat.getNumberInstance(Locale.US);

System.out.println(vi.format(value));
System.out.println(us.format(value));
```

The exact output comes from locale data, but grouping and decimal separators commonly differ.

Specialized factories include:

```java
NumberFormat.getIntegerInstance(locale);
NumberFormat.getPercentInstance(locale);
NumberFormat.getCurrencyInstance(locale);
```

For simpler `printf`/`String.format` style formatting, Java also provides overloads that accept a `Locale`:

```java
String text = String.format(Locale.US, "%,.2f", 1234.5);
```

When the locale argument is omitted, formatting APIs of this kind can rely on the default locale. Passing the locale explicitly makes user-facing formatting easier to reason about and test.

Choose the factory by presentation meaning rather than by whichever output happens to look convenient.

For example:

```java
NumberFormat percent = NumberFormat.getPercentInstance(Locale.US);
System.out.println(percent.format(0.25));
```

The domain value remains `0.25`; the percent sign and scaling belong to presentation.

## <a id="decimal-format">DecimalFormat Patterns and Symbols</a>

When presentation needs more control, a `DecimalFormat` can combine a pattern with locale-specific symbols:

```java
DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
DecimalFormat format = new DecimalFormat("#,##0.00", symbols);

String text = format.format(new BigDecimal("1234.5"));
```

Pattern vocabulary includes:

```text
#  → optional digit
0  → required digit
.  → decimal separator position in pattern syntax
,  → grouping separator position in pattern syntax
```

`DecimalFormatSymbols` determines the actual locale characters used for decimal/grouping separators, percent signs, and related symbols.

Do not format and then replace `.` with `,` manually. Configure the formatter correctly.

### Rounding is a domain policy when business meaning depends on it

Formatting can round the displayed representation:

```java
format.setMaximumFractionDigits(2);
format.setRoundingMode(RoundingMode.HALF_UP);
```

But tax, payment, or accounting rounding rules belong to the business domain. Choosing another locale must never silently change the amount owed.

## <a id="compact-number-format">CompactNumberFormat and Compact Number Styles</a>

Some interfaces do not want to display the full value `1,200,000`; they want a shorter human-facing form such as “1.2M” or a longer compact phrase appropriate for the locale.

Java 21 supports this through `NumberFormat.getCompactNumberInstance(...)`.

```java
NumberFormat shortFormat = NumberFormat.getCompactNumberInstance(
        Locale.US,
        NumberFormat.Style.SHORT
);

NumberFormat longFormat = NumberFormat.getCompactNumberInstance(
        Locale.US,
        NumberFormat.Style.LONG
);

System.out.println(shortFormat.format(1_200_000));
System.out.println(longFormat.format(1_200_000));
```

Mental model:

```text
numeric value
→ 1_200_000

Locale + compact Style
→ SHORT / LONG

CompactNumberFormat
→ user-facing compact representation
```

`SHORT` and `LONG` do not mean the application should concatenate `K`, `M`, or `B` itself. Compact patterns come from locale data because different languages can abbreviate large numbers differently.

This is still **presentation only**. The domain value remains `1_200_000`; the compact string should not replace the canonical numeric value.

Compact formatting is useful for dashboards, statistic cards, and space-constrained UI. In invoices, accounting, or other precision-sensitive contexts, a full representation is usually more appropriate.

## <a id="parsing-numbers">Locale-Sensitive Number Parsing</a>

Human input may also require the locale that produced it:

```java
NumberFormat format = NumberFormat.getNumberInstance(
        Locale.forLanguageTag("vi-VN")
);

Number parsed = format.parse(input);
```

The same text can be interpreted differently under another locale, so a server should not parse localized user input through an accidental JVM default.

`NumberFormat.parse(String)` can accept a valid prefix and stop before the rest. When validation requires the **entire input** to be consumed, use `ParsePosition`:

```java
ParsePosition pos = new ParsePosition(0);
Number number = format.parse(input, pos);

boolean valid = number != null
        && pos.getIndex() == input.length()
        && pos.getErrorIndex() < 0;
```

For decimal domain values, `DecimalFormat` can parse to `BigDecimal`:

```java
DecimalFormat decimal = (DecimalFormat) NumberFormat.getNumberInstance(locale);
decimal.setParseBigDecimal(true);
```

This avoids introducing binary floating-point approximation merely because the value passed through a UI parser.

## <a id="formatting-vs-domain-value">Formatting Must Not Change Domain Meaning</a>

Keep the direction explicit:

```text
BigDecimal / numeric domain value
        ↓
calculation, validation, persistence
        ↓
human presentation boundary
        ↓
NumberFormat(locale)
        ↓
String for the user
```

Do not persist the formatted representation as the primary numeric value:

```java
String formatted = format.format(total);
// not the canonical amount to store and calculate with
```

Formatted text may contain locale-specific separators, lose displayed precision, or fail under another parser. Machine-to-machine serialization needs its own stable, locale-neutral contract.

The next chapter adds another frequently confused concept: currency. A locale can influence currency display and can suggest a regional default, but the currency of a transaction is separate domain information.
