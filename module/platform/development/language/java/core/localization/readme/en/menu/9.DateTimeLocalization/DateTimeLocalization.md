# Date-Time Localization

Date-time presentation is a common place to confuse `Locale` with `ZoneId`. They solve different problems: locale controls **presentation conventions**, while a time zone controls **the relationship between an instant and local clock time**.

**Date-time localization** means rendering a date/time value in a form familiar to readers of a locale, including conventions such as date-field order, localized month/day names, and the requested level of detail.

A complete date-time presentation pipeline may contain:

```text
temporal value
→ LocalDate / LocalDateTime / Instant / ZonedDateTime...

ZoneId (when an instant must become local time)
→ determines which local date/time is meant

Locale
→ determines language/regional presentation conventions

DateTimeFormatter + style/pattern
→ produces the final String
```

Date-time localization therefore **does not change the meaning of time**; it changes how an already-defined date/time is presented to a person.

## <a id="localized-date-format">Localized Date and Time Styles</a>

`DateTimeFormatter` can request a localized style instead of hard-coding one date pattern for every user:

```java
DateTimeFormatter formatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.forLanguageTag("vi-VN"));

String text = formatter.format(LocalDate.of(2026, 9, 27));
```

The standard styles are:

```text
FULL
LONG
MEDIUM
SHORT
```

Related factories include:

```java
DateTimeFormatter.ofLocalizedTime(style);
DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle);
```

The application specifies the **level of detail**, while locale data selects an appropriate pattern:

```text
application asks for MEDIUM date
        ↓
vi-VN → Vietnamese presentation convention
en-US → US-English presentation convention
```

The exact rendered string can depend on the locale data available in the runtime. If the business contract only requires localized presentation, tests should avoid unnecessarily freezing one implementation-specific literal pattern.

## <a id="locale-vs-zone">Locale vs ZoneId Responsibilities</a>

Keep this model explicit:

```text
Instant
→ one point on the timeline

ZoneId
→ maps that instant to a local date/time using time-zone rules

Locale
→ chooses how that local date/time is presented to a reader
```

Start from one instant:

```java
Instant instant = Instant.parse("2026-09-27T08:30:00Z");

ZoneId hcm = ZoneId.of("Asia/Ho_Chi_Minh");
ZoneId newYork = ZoneId.of("America/New_York");

ZonedDateTime vietnamTime = instant.atZone(hcm);
ZonedDateTime newYorkTime = instant.atZone(newYork);
```

Then choose how one of those local views should be shown:

```java
DateTimeFormatter viFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.forLanguageTag("vi-VN"));

String display = viFormatter.format(vietnamTime);
```

It is also valid to format `newYorkTime` using `vi-VN`. That simply means “show New York local time using Vietnamese presentation conventions.”

Therefore:

```text
Locale.forLanguageTag("vi-VN")
does not imply
ZoneId.of("Asia/Ho_Chi_Minh")
```

User language preference and user time zone should be modeled independently.

## <a id="locale-week-conventions">WeekFields and Locale-Sensitive Week Conventions</a>

Locale can affect more than the **spelling** of a date. Some human calendar conventions differ between locales, especially the definition of a **week**.

Two questions that look universal are not actually answered the same way everywhere:

```text
Which day starts the week?
How many days must the first week of a year contain?
```

Java represents these conventions with `WeekFields`:

```java
WeekFields weekFields = WeekFields.of(locale);

DayOfWeek firstDay = weekFields.getFirstDayOfWeek();
int minimalDays = weekFields.getMinimalDaysInFirstWeek();
```

Important pieces are:

```text
first day of week
→ the day considered to start a week

minimal days in first week
→ minimum days required for a week to count as the year's first week

week-based fields
→ weekOfMonth / weekOfYear / weekOfWeekBasedYear...
```

`WeekFields` therefore provides **locale-sensitive calendar conventions** for presentation and calendar-style use cases.

Keep one boundary explicit:

```text
locale-sensitive calendar convention
≠ business rule
```

If the domain says “the accounting week always begins Monday,” encode that rule explicitly with `WeekFields.of(DayOfWeek.MONDAY, ...)` or another business policy. A user's locale should not accidentally change business logic.

## <a id="localized-numbering-calendar">DecimalStyle and Unicode Locale Extensions</a>

`Locale` can carry more than language and region through **Unicode locale extensions**. Some date-time APIs can use those extensions to select calendar systems, numbering systems, region overrides, or time-zone overrides for presentation.

This does **not** change the foundational model:

```text
Locale is still not ZoneId.
vi-VN does not automatically mean Asia/Ho_Chi_Minh.
Business/user time-zone preference should still be modeled separately.
```

It simply explains why some localization APIs can read additional preferences when code deliberately provides a locale containing such extensions.

For date-time formatting, `DecimalStyle` describes the numeric symbols used by a formatter:

```java
DecimalStyle style = DecimalStyle.of(locale);

char zeroDigit = style.getZeroDigit();
char positiveSign = style.getPositiveSign();
char negativeSign = style.getNegativeSign();
char decimalSeparator = style.getDecimalSeparator();
```

`DateTimeFormatter` also has two methods worth distinguishing:

```java
formatter.withLocale(locale);
formatter.localizedBy(locale);
```

Beginner mental model:

```text
withLocale(locale)
→ changes the locale used for localized text/pattern behavior

localizedBy(locale)
→ applies the locale more broadly, including relevant locale extensions
```

Applications should not put Unicode extensions into every request without a real use case. The learning goal is simply to understand that **Locale can carry extended preferences and some formatters know how to interpret them**, without turning Locale into a container for all business context.

## <a id="localized-pattern">Localized Pattern Generation</a>

Most application code can use the localized formatter factories directly. Sometimes a framework, report engine, or integration needs to inspect the concrete localized pattern. `DateTimeFormatterBuilder.getLocalizedDateTimePattern(...)` exposes it:

```java
String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.MEDIUM,
        null,
        IsoChronology.INSTANCE,
        locale
);
```

This is useful when another component specifically requires a pattern string.

Do not immediately copy that result into application constants. If the intent is “use the locale's medium date style,” preserving the higher-level `ofLocalizedDate(...)` request lets locale data continue to own the pattern.

If the application contract truly requires an explicit pattern, use one deliberately:

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
        "d MMMM uuuu",
        locale
);
```

At that point the application owns the pattern while the locale still controls textual elements such as localized month names.

## <a id="localized-parsing">Locale-Sensitive Date-Time Parsing</a>

Human-entered text may need the same locale context used for display, especially when it contains localized month/day names or a locale-specific structure:

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
        "d MMMM uuuu",
        Locale.forLanguageTag("vi-VN")
);

LocalDate date = LocalDate.parse(input, formatter);
```

Machine-to-machine contracts should normally use a stable explicit format instead:

```java
Instant.parse("2026-09-27T08:30:00Z");
LocalDate.parse("2026-09-27");
```

Use this boundary:

```text
human display/input
→ locale-aware formatter when appropriate

machine contract / persistence
→ explicit stable representation, commonly ISO-based
```

Parsing localized input also requires an intentional validation/resolver policy and handling of `DateTimeParseException`. A human-friendly display string should not automatically become the canonical stored representation.

The next chapter examines a behavior first encountered with `ResourceBundle`: **fallback**. Fallback makes localized resources reusable, but it can also hide missing translations when the resolution chain is not understood.
