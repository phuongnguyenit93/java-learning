# LocalDate

Once date, time-of-day, and timeline instants are separated, `LocalDate` is the simplest place to start. It represents an ISO-8601 **calendar date with no time of day and no time zone**.

## <a id="local-date-model">LocalDate Model</a>

`LocalDate` is a good fit when the business fact is a date on the calendar.

```java
LocalDate birthday = LocalDate.of(1993, 7, 20);
LocalDate invoiceDate = LocalDate.of(2026, 9, 27);
```

These values contain fields such as year, month, and day-of-month. They do not contain an hour, `ZoneId`, or `ZoneOffset`.

### Why not use LocalDateTime everywhere?

It is possible to invent midnight:

```java
LocalDateTime birthday = LocalDateTime.of(1993, 7, 20, 0, 0);
```

But the model now claims something the domain never said: “the birthday occurs at midnight.” That invented field can later leak into serialization, database queries, or zone conversion.

Prefer:

```text
Domain owns only a date
        ↓
LocalDate
        ↓
add time/zone only when a real use case requires them
```

### Creating and reading LocalDate values

```java
LocalDate explicit = LocalDate.of(2026, 9, 27);
LocalDate parsed = LocalDate.parse("2026-09-27");
LocalDate today = LocalDate.now();
```

`LocalDate.now()` depends on a clock and zone from the environment. Later, the `Clock` chapter will show how to avoid scattering hidden time dependencies throughout business logic.

```java
int year = explicit.getYear();
Month month = explicit.getMonth();
int monthValue = explicit.getMonthValue();
int day = explicit.getDayOfMonth();
DayOfWeek dayOfWeek = explicit.getDayOfWeek();
```

## <a id="local-date-arithmetic">LocalDate Arithmetic</a>

Because `LocalDate` is immutable, arithmetic returns new values:

```java
LocalDate date = LocalDate.of(2026, 9, 27);

LocalDate tomorrow = date.plusDays(1);
LocalDate nextWeek = date.plusWeeks(1);
LocalDate nextMonth = date.plusMonths(1);
LocalDate lastYear = date.minusYears(1);
```

Beyond convenience methods such as `plusDays`, Java also has shared abstractions for temporal amounts and units. The next snippet is only a **preview**: `Period` is explained fully in the `Duration / Period` chapter, and `ChronoUnit` in `Arithmetic / Comparison`:

```java
LocalDate afterTenDays = date.plus(Period.ofDays(10));
long days = ChronoUnit.DAYS.between(date, afterTenDays);
```

The key point is that this is **calendar arithmetic**. `plusDays(1)` moves to the next calendar date; it does not promise that exactly 24 elapsed timeline hours have passed. That distinction becomes important once a zone with daylight-saving transitions enters the model.

### Field-based adjustment

```java
LocalDate firstDay = date.withDayOfMonth(1);
LocalDate december = date.withMonth(12);
```

For reusable calendar adjustments, Java provides `TemporalAdjusters`:

```java
LocalDate lastDayOfMonth = date.with(TemporalAdjusters.lastDayOfMonth());
LocalDate nextMonday = date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
```

Do not mistake general calendar adjusters for a business calendar. “Next business day” can depend on weekends, holidays, country, exchange, or company policy and belongs in application policy.

## <a id="calendar-validity">Calendar Validity and Month-End Behavior</a>

`LocalDate` always represents a valid date in its calendar model. Java does not keep an impossible value such as “February 30.”

```java
LocalDate.of(2026, 2, 30); // DateTimeException
```

### Leap years

```java
LocalDate leapDay = LocalDate.of(2024, 2, 29);
boolean leap = leapDay.isLeapYear();
```

Creating `2025-02-29` fails because that date does not exist.

### Adding months at the end of a month

Consider:

```java
LocalDate january31 = LocalDate.of(2026, 1, 31);
LocalDate result = january31.plusMonths(1);
```

February 2026 has no day 31. `plusMonths` resolves the result to the last valid day of the target month, producing `2026-02-28`.

```text
2026-01-31
    + 1 calendar month
        ↓
target month = 2026-02
        ↓
day 31 is invalid
        ↓
use last valid day = 28
```

That behavior is fundamentally different from adding a fixed number of seconds.

### When is LocalDate the right type?

Use it when the value does not need to answer either of these questions:

```text
What time of day?
Which exact global instant?
```

Examples include birthdays, invoice dates, local expiry dates, and holidays. If the application starts asking “at what time?” or “in which zone?”, that is a signal to move to another temporal type instead of encoding conventions around `LocalDate`.
