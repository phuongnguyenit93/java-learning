# Formatting and Parsing

Date-time objects and text are different representations. A value such as `LocalDate` or `Instant` carries temporal semantics; strings such as `27/09/2026`, `2026-09-27`, or `09:30 AM` are **representations** used at UI, file, API, or protocol boundaries.

A common mistake is to let the text shape become the time model. Keep these operations separate instead:

```text
format
→ temporal object → text

parse
→ text → temporal object
```

## <a id="date-time-formatter">DateTimeFormatter and Immutability</a>

`DateTimeFormatter` is the main formatter/parser type in `java.time`. It is immutable and thread-safe, so a formatter can be reused instead of recreated for every request.

`DateTimeFormatterBuilder` is **a builder for composing a more complex formatter from several pieces** when a predefined formatter or one simple pattern string is not enough, for example when optional sections, special literals, or combined parsing/formatting rules are required. Beginners do not need it for ordinary cases; the important point is that it builds a `DateTimeFormatter` rather than representing another kind of time.

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu");

LocalDate date = LocalDate.of(2026, 9, 27);
String text = date.format(formatter);
// 27/09/2026
```

Parsing runs in the opposite direction:

```java
LocalDate parsed = LocalDate.parse("27/09/2026", formatter);
```

### Formatter as boundary policy

One domain value:

```java
LocalDate invoiceDate = LocalDate.of(2026, 9, 27);
```

can be rendered many ways:

```text
27/09/2026
09/27/2026
2026-09-27
27 Sep 2026
```

The domain model should not change merely because a UI wants another layout. Formatting belongs at presentation or serialization boundaries.

### Predefined formatters

Java provides standard formatter contracts:

```java
LocalDate date = LocalDate.parse("2026-09-27", DateTimeFormatter.ISO_LOCAL_DATE);
Instant instant = Instant.parse("2026-09-27T10:15:30Z");
OffsetDateTime offset = OffsetDateTime.parse("2026-09-27T17:15:30+07:00");
```

When a protocol already follows an ISO representation, predefined formatters are usually easier to reason about than inventing custom patterns.

### Formatting an Instant needs local context when the output contains calendar fields

`Instant` knows only the timeline point. To render year/month/day/hour fields for a region, the formatter or conversion step must have a zone:

```java
Instant instant = Instant.parse("2026-09-27T07:30:00Z");

DateTimeFormatter display = DateTimeFormatter
        .ofPattern("dd/MM/uuuu HH:mm")
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

String text = display.format(instant);
```

If a custom pattern asks for fields such as year or hour while formatting an `Instant` without supplying a zone, there are not enough local calendar fields to satisfy the pattern and the operation can fail with `UnsupportedTemporalTypeException`.

```text
Instant
→ knows "when"

ZoneId
→ knows "viewed from where"

DateTimeFormatter
→ knows "how to write it as text"
```

## <a id="format-patterns">Patterns vs Predefined Formatters</a>

Custom patterns are useful when a UI or legacy file format has a real requirement:

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm");
LocalDateTime value = LocalDateTime.of(2026, 9, 27, 14, 30);

String text = formatter.format(value);
// 27-09-2026 14:30
```

Pattern letters have their own semantics and should not be guessed from appearance.

Important examples:

```text
MM → month-of-year
mm → minute-of-hour

HH → hour-of-day 00-23
hh → clock-hour-of-am-pm 01-12

uuuu → proleptic year
yyyy → year-of-era
YYYY → week-based-year
```

Confusing `MM` and `mm`, or `yyyy` and `YYYY`, is a common real-world bug.

### `uuuu` and `yyyy`

For many modern business dates, `uuuu` is easier to use with strict ISO-style parsing because it represents the **proleptic year**: years are numbered continuously across eras, including year `0` and negative years when moving before the current era.

`yyyy` is the **year-of-era**: the year number inside an era such as CE/BCE, so a complete interpretation also involves the era.

`YYYY` is different again: it is the **week-based-year**, whose year boundary follows week rules rather than the ordinary calendar-year boundary. Around the end or beginning of a calendar year, a date can belong to a different week-based-year. Therefore do not use `YYYY-MM-dd` when the intent is an ordinary calendar date.

```java
DateTimeFormatter strictDate = DateTimeFormatter
        .ofPattern("dd/MM/uuuu")
        .withResolverStyle(ResolverStyle.STRICT);
```

The lesson is not “never use yyyy.” The lesson is that **pattern letters carry semantics**, not merely output shape.

### Locale affects textual fields

Patterns containing month/day names need an intentional locale:

```java
DateTimeFormatter english = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
```

`Locale` here is the language/region **presentation context** used for text such as month or weekday names. It does not replace `ZoneId` and does not determine the instant. Detailed localization belongs to the `localization` module. Here, the important boundary is that formatter behavior can depend on locale, so machine defaults should not silently define a deterministic external contract.

## <a id="strict-smart-lenient">ResolverStyle: STRICT, SMART, LENIENT</a>

Parsing is more than splitting characters. After fields are read, Java must **resolve** them into a valid temporal value.

The main resolver styles are:

```text
STRICT
→ require fields to satisfy the rules precisely

SMART
→ allow selected sensible adjustments according to formatter semantics

LENIENT
→ allow broader overflow and normalization
```

A formatter created by `DateTimeFormatter.ofPattern(...)` uses **`SMART` by default**. Predefined formatters can carry their own resolver configuration, so do not generalize that every `DateTimeFormatter` defaults to `SMART`. If an input contract must reject invalid calendar data strictly, inspect or configure the `ResolverStyle` intentionally.

Example:

```java
DateTimeFormatter strict = DateTimeFormatter
        .ofPattern("dd/MM/uuuu")
        .withResolverStyle(ResolverStyle.STRICT);

LocalDate.parse("31/02/2026", strict); // DateTimeParseException
```

For user/API input with a strict contract, `STRICT` often gives easier reasoning because invalid calendar data is rejected instead of silently adjusted.

### SMART is not business validation

Even a successfully parsed value can violate application rules:

```text
valid calendar date
≠
valid booking date
```

A booking might need to be within the next 90 days or avoid company holidays. `DateTimeFormatter` validates/resolves temporal text; it does not replace business validation.

## <a id="parse-target-type">Parse into the Correct Temporal Type</a>

Choose a target type that preserves the information actually present in the text.

```java
LocalDate date = LocalDate.parse("2026-09-27");
LocalDateTime local = LocalDateTime.parse("2026-09-27T14:30:00");
OffsetDateTime offset = OffsetDateTime.parse("2026-09-27T14:30:00+07:00");
ZonedDateTime zoned = ZonedDateTime.parse("2026-09-27T14:30:00+07:00[Asia/Ho_Chi_Minh]");
Instant instant = Instant.parse("2026-09-27T07:30:00Z");
```

### Do not parse and throw information away

If the input contains an offset:

```text
2026-09-27T14:30:00+07:00
```

manually stripping `+07:00` and parsing only a `LocalDateTime` loses timeline context. Parse into `OffsetDateTime` or another appropriate type first, then convert intentionally.

### Do not invent information that the text does not contain

If the input is only:

```text
2026-09-27T14:30:00
```

do not assume UTC or the JVM's default zone unless an external contract explicitly says so.

```text
fields actually present in the text
        ↓
parse into a type that preserves their meaning
        ↓
add zone/offset only when the contract/domain supplies it
```

Once parsing has preserved the right semantics, the next question is arithmetic and comparison: adding, subtracting, and ordering values whose temporal meanings are not all the same.
