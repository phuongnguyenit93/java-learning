# Duration and Period

“Add one day” sounds similar to “add 24 hours,” but those statements do not always mean the same thing in a date-time domain. Java separates them with two models: `Duration` and `Period`.

## <a id="duration-time-based">Duration — Timeline-Based Amount</a>

`Duration` represents a time-based amount, fundamentally in seconds plus nanoseconds.

```java
Duration thirtyMinutes = Duration.ofMinutes(30);
Duration twoHours = Duration.ofHours(2);
Duration oneDayAsTime = Duration.ofDays(1); // exactly 24 hours
```

Mental model:

```text
Duration.ofHours(24)
→ 86,400 seconds of elapsed timeline time
```

This is a natural fit for timeouts, latency, TTL, and elapsed time between timeline points.

```java
Instant start = Instant.parse("2026-10-05T02:00:00Z");
Instant end = Instant.parse("2026-10-05T03:30:00Z");

Duration elapsed = Duration.between(start, end);
System.out.println(elapsed.toMinutes()); // 90
```

A duration can be negative when the end precedes the start.

## <a id="period-date-based">Period — Calendar-Based Amount</a>

`Period` represents an amount in **years, months, and days**.

```java
Period oneMonth = Period.ofMonths(1);
Period oneYearTwoMonths = Period.of(1, 2, 0);
Period oneCalendarDay = Period.ofDays(1);
```

It fits calendar-based rules:

```java
LocalDate start = LocalDate.of(2026, 1, 31);
LocalDate next = start.plus(Period.ofMonths(1));
// 2026-02-28
```

`Period.ofMonths(1)` cannot be defined as one fixed number of seconds because months have different lengths.

### Period does not magically normalize every unit

```java
Period p = Period.of(0, 15, 0);
System.out.println(p);              // P15M
System.out.println(p.normalized()); // P1Y3M
```

Years and months have a useful normalization relationship, but days cannot generally be converted to months because month length is not fixed.

## <a id="duration-vs-period">Duration vs Period Semantics</a>

Choose from the question being asked:

```text
“How much real timeline time elapsed?”
→ Duration

“How many calendar days/months/years?”
→ Period
```

### DST: 24 hours can differ from one calendar day

In a region with daylight-saving transitions:

```java
ZonedDateTime start = ZonedDateTime.of(
        LocalDateTime.of(2026, 3, 28, 12, 0),
        ZoneId.of("Europe/Paris")
);

ZonedDateTime plus24Hours = start.plus(Duration.ofHours(24));
ZonedDateTime plusOneDay = start.plus(Period.ofDays(1));
```

`plus24Hours` preserves **24 hours of elapsed timeline duration**. `plusOneDay` expresses **the next calendar day with local-time semantics**. Around a DST transition, the elapsed timeline distance for the latter can be 23 or 25 hours.

That is why `Period.ofDays(1)` and `Duration.ofHours(24)` must not be treated as interchangeable just because they often appear equivalent on ordinary days.

### Month length breaks fixed-second assumptions too

```text
1 month
→ 28, 29, 30, or 31 days depending on position

1 year
→ may include a leap day
```

There is no context-free exact conversion from `Period.ofMonths(1)` to a `Duration`.

### Quick choice table

| Need | Choose |
| --- | --- |
| HTTP timeout of 30 seconds | `Duration` |
| cache TTL of 10 minutes | `Duration` |
| processing time between two `Instant` values | `Duration` |
| add one month to an invoice date | `Period` or `plusMonths` |
| age-like years/months/days | `Period` |
| “same local time tomorrow” scheduling | calendar arithmetic, usually `Period`/`plusDays` on a zone-aware value |

Once the temporal value and amount semantics are correct, a very common boundary comes next: converting between **text and date-time values**.
