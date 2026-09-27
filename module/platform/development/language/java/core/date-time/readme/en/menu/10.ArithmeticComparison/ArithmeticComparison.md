# Date-Time Arithmetic and Comparison

Date-time arithmetic is not merely “add a number.” Adding `1 day`, `24 hours`, or `1 month`, and measuring distance between two values, all depend on the temporal type and semantic unit involved.

## <a id="temporal-arithmetic">Temporal Arithmetic Semantics</a>

Most `java.time` types provide operations such as:

```java
value.plusDays(1);
value.minusHours(2);
value.plus(amount);
value.minus(amount);
```

But their meaning follows the type.

### LocalDate

```java
LocalDate invoiceDate = LocalDate.of(2026, 1, 31);
LocalDate nextMonth = invoiceDate.plusMonths(1);
// 2026-02-28
```

This is calendar arithmetic.

### Instant

```java
Instant deadline = Instant.parse("2026-09-27T10:00:00Z");
Instant extended = deadline.plus(Duration.ofMinutes(30));
```

This is timeline arithmetic.

### ZonedDateTime

`ZonedDateTime` carries both local calendar fields and timeline context, so this distinction matters:

```java
zoned.plusDays(1);                 // date-based/local semantics
zoned.plus(Duration.ofHours(24)); // timeline-duration semantics
```

Around a DST transition, these operations may not produce the same local clock time or the same elapsed seconds.

### TemporalAmount and TemporalUnit

When reading Javadoc, a beginner will often encounter two overload shapes:

```java
temporal.plus(amount);
temporal.plus(number, unit);
```

They use different abstractions:

- `TemporalAmount` is **a structured amount of time**, such as `Period.ofMonths(2)` or `Duration.ofMinutes(30)`;
- `TemporalUnit` is **the unit that gives meaning to a numeric quantity**, such as DAYS, HOURS, or MONTHS;
- `ChronoUnit` is Java's standard enum implementation of `TemporalUnit`, providing familiar units such as `NANOS`, `SECONDS`, `MINUTES`, `HOURS`, `DAYS`, `WEEKS`, `MONTHS`, and `YEARS`.

Mental model:

```text
TemporalAmount
→ "add this amount"
→ Period / Duration

TemporalUnit
→ "which unit gives this number meaning?"

ChronoUnit
→ Java's standard set of TemporalUnit values
```

Type relationship:

```text
TemporalAmount
├── Duration
└── Period

TemporalUnit
└── ChronoUnit
```

Two styles can express the same intent:

```java
LocalDate date = LocalDate.of(2026, 9, 27);

LocalDate byAmount = date.plus(Period.ofWeeks(2));
LocalDate byUnit = date.plus(2, ChronoUnit.WEEKS);
```

Why does Java need these shared abstractions? They let different temporal types share vocabulary such as `plus`, `minus`, `between`, fields, and units instead of every class inventing a completely unrelated operation model.

Shared vocabulary does **not** mean every unit is valid for every type. `LocalDate` has no hour-of-day and therefore does not support `HOURS`; `Instant` has no calendar-month semantics and does not directly support `MONTHS`. Unsupported unit/type combinations can throw `UnsupportedTemporalTypeException`.

## <a id="between-semantics">Semantics of between</a>

There are several ways to ask “how far apart are A and B?”, and they answer different semantic questions.

### ChronoUnit.between

```java
LocalDate start = LocalDate.of(2026, 9, 1);
LocalDate end = LocalDate.of(2026, 9, 27);

long days = ChronoUnit.DAYS.between(start, end); // 26
```

`ChronoUnit` reports a count of complete units between compatible temporal values.

### Duration.between

```java
Instant a = Instant.parse("2026-09-27T10:00:00Z");
Instant b = Instant.parse("2026-09-27T11:30:00Z");

Duration elapsed = Duration.between(a, b);
```

This is appropriate for elapsed timeline time.

### Period.between

```java
LocalDate birth = LocalDate.of(1993, 7, 20);
LocalDate date = LocalDate.of(2026, 9, 27);

Period calendarDifference = Period.between(birth, date);
```

The result preserves calendar year/month/day components; it is not a total number of seconds.

### Whole-unit boundaries

Operations such as `ChronoUnit.HOURS.between` count whole units according to the supported temporal semantics. If the application needs fractional units, keep a higher-precision representation such as `Duration`/nanoseconds and apply the domain's rounding policy explicitly.

### Time ranges need explicit inclusive/exclusive boundaries

`java.time` does not impose one core `Interval` class on every application, so a domain normally defines its own range semantics. A very common convention is a **half-open interval**:

```text
[start, end)

start     → inclusive
end       → exclusive
```

For example, an event belongs to the range when:

```java
boolean inside = !event.isBefore(start) && event.isBefore(endExclusive);
```

This convention is especially useful for “all events on one date” queries because it avoids inventing a value such as `23:59:59.999999999`:

```java
LocalDate day = LocalDate.of(2026, 9, 27);
ZoneId zone = ZoneId.of("Europe/Paris");

Instant start = day.atStartOfDay(zone).toInstant();
Instant endExclusive = day.plusDays(1).atStartOfDay(zone).toInstant();
```

`LocalDate.atStartOfDay(zone)` does not blindly force `00:00`. If midnight falls inside a zone transition/gap, Java returns the **earliest valid time** for that date in the zone. That makes `[startOfDay, startOfNextDay)` safer than manually constructing `00:00`/`23:59:59...` when the domain asks for a region's calendar day.

## <a id="date-time-comparison">Compare Temporal Values by the Right Meaning</a>

Temporal types often expose:

```java
a.isBefore(b);
a.isAfter(b);
a.compareTo(b);
a.equals(b);
```

The important question is **what kind of ordering or equality is being asked for?**

### LocalDate comparison

```java
LocalDate a = LocalDate.of(2026, 9, 27);
LocalDate b = LocalDate.of(2026, 9, 28);

a.isBefore(b); // true
```

This orders calendar dates.

### Instant comparison

```java
instantA.isBefore(instantB);
```

This orders points on the global timeline.

### Zoned values: representation vs timeline identity

Two `ZonedDateTime` values can have different local fields/zones while representing the same instant. If the business question is “same moment on the timeline?”, comparing their instants states that intent clearly:

```java
boolean sameMoment = a.toInstant().equals(b.toInstant());
boolean sameMomentDirectly = a.isEqual(b);
```

`isEqual(...)` on suitable zone-aware temporals expresses timeline equality directly. Do not use `equals()` as a universal shorthand for every meaning of “same time.” Object equality follows the state/contract of the specific temporal type.

For `ZonedDateTime` and `OffsetDateTime`, read the APIs in two groups:

```text
isEqual / isBefore / isAfter
→ questions about the instant/global timeline

equals
→ object-representation equality according to the type's contract

compareTo
→ the type's natural ordering, with tie-breaks that keep ordering consistent with equality
→ do not assume it is an "instant-only comparator"
```

Here:

```text
natural ordering
→ the default order defined by compareTo(...) for the type

tie-break
→ an extra comparison criterion used only when the previous criterion is equal
```

When business logic cares only about timeline order, `toInstant()` or `isBefore` / `isAfter` / `isEqual` usually expresses that intent more clearly.

**Advanced boundary — exact contracts:** beginners only need the rule above first; the block below matters when sorting/comparator code must follow the exact type contract.

```text
OffsetDateTime.equals(...)
→ same local date-time + same offset

ZonedDateTime.equals(...)
→ same local date-time + same offset + same ZoneId

isEqual / isBefore / isAfter
→ compare the instant on the timeline

OffsetDateTime.compareTo(...)
→ compare instant first
→ when instants tie, local date-time is a tie-break

ZonedDateTime.compareTo(...)
→ compare instant first
→ then local date-time
→ then ZoneId
→ chronology (calendar system) is the final tie-break in the ChronoZonedDateTime contract
```

Therefore two values can represent **the same instant while `compareTo(...) != 0`**. When a comparator must use only timeline order, `OffsetDateTime.timeLineOrder()` and `ChronoZonedDateTime.timeLineOrder()` exist for that purpose.

### Equal local fields do not create global equality

```java
LocalDateTime vietnamNine = LocalDateTime.of(2026, 10, 5, 9, 0);
LocalDateTime parisNine = LocalDateTime.of(2026, 10, 5, 9, 0);
```

Those values are equal as local fields, but that does not prove that real events at 09:00 in Vietnam and Paris occur at the same instant. Zone context must be applied first.

## <a id="business-calendar-boundary">Business Calendars Are Separate Policy</a>

The JDK knows calendar mechanics; it does not know your organization's business-day policy.

“Three business days from now” may require answers to:

```text
Does Saturday count?
Sunday?
Which country's public holidays?
Company-specific holidays?
What happens after a 17:00 cut-off?
```

This is not correctly modeled as:

```java
deadline = start.plusDays(3);
```

`plusDays(3)` means three calendar days, not three business days.

A clearer design can make the policy explicit:

```java
interface BusinessCalendar {
    LocalDate addBusinessDays(LocalDate start, int days);
    boolean isBusinessDay(LocalDate date);
}
```

An implementation can use `LocalDate`, `DayOfWeek`, and a separate holiday source. The date-time API provides **mechanics**; the application supplies **business policy**.

The next chapter addresses another frequently hidden dependency: asking the operating environment what time it is “now.”
