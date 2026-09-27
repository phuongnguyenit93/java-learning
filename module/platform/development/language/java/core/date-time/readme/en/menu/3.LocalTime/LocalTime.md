# LocalTime

`LocalTime` models a **time within a day** without attaching a date or time zone. It fits statements such as “the store opens at 08:30” when the date and zone, if needed, belong elsewhere in the model.

## <a id="local-time-model">LocalTime Model</a>

```java
LocalTime openingTime = LocalTime.of(8, 30);
LocalTime closingTime = LocalTime.of(21, 0);
```

The value `08:30` by itself does not answer:

```text
which date?
which country or region?
what UTC offset?
has this happened on the timeline yet?
```

That is not missing functionality; it is the contract of `LocalTime`.

### Why not invent a date?

If the business rule is “open every day at 08:30,” creating a `LocalDateTime` with a fake date such as `1970-01-01T08:30` mixes invented data with real data and can later leak that fake date into serialization or comparisons.

```text
Only time-of-day is known
        ↓
LocalTime
        ↓
combine with LocalDate only when a real schedule needs a date
```

```java
LocalDate date = LocalDate.of(2026, 10, 5);
LocalTime time = LocalTime.of(9, 0);

LocalDateTime meeting = LocalDateTime.of(date, time);
// or date.atTime(time)
```

## <a id="local-time-precision">Nanosecond Precision</a>

`LocalTime` can hold:

```text
hour
minute
second
nano-of-second
```

```java
LocalTime precise = LocalTime.of(9, 30, 15, 123_456_789);
```

This means the type can **represent** nanoseconds. It does not mean the operating system, hardware clock, database, or upstream source measured the event accurately to a nanosecond.

```text
representation precision
≠
measurement accuracy/resolution
```

Persistence boundaries matter too. A database column that supports only microseconds or milliseconds may truncate finer precision.

Useful constants and field access:

```java
LocalTime midnight = LocalTime.MIDNIGHT;
LocalTime noon = LocalTime.NOON;

int hour = precise.getHour();
int minute = precise.getMinute();
int second = precise.getSecond();
int nano = precise.getNano();
```

## <a id="local-time-wrap">Arithmetic Wraps Within the Day</a>

Because `LocalTime` contains no date, arithmetic that crosses midnight wraps within the 24-hour day.

```java
LocalTime late = LocalTime.of(23, 30);
LocalTime later = late.plusHours(2);

System.out.println(later); // 01:30
```

The result does not contain “tomorrow” because there is no calendar date to increment.

```java
LocalTime time = LocalTime.of(10, 15);

time.plusHours(24);    // 10:15
time.minusMinutes(30); // 09:45
```

### Pitfall: elapsed time across midnight

Suppose a shift starts at `22:00` and ends at `06:00`:

```java
LocalTime start = LocalTime.of(22, 0);
LocalTime end = LocalTime.of(6, 0);
```

Those two values do not tell you whether `06:00` is on the same date or the next date. If elapsed time matters, the model needs a date or an explicit scheduling rule.

```java
LocalDate workDate = LocalDate.of(2026, 9, 27);
LocalDateTime startDateTime = workDate.atTime(start);
LocalDateTime endDateTime = workDate.plusDays(1).atTime(end);

Duration duration = Duration.between(startDateTime, endDateTime);
```

Here `Duration` is only a preview of an “elapsed amount” once a date has been supplied. The `Duration / Period` chapter later explains what `Duration` is, how it differs from `Period`, and why `24 hours` is not always equivalent to `1 calendar day`.

If the shift is tied to a real region with DST, local date-time alone is still not enough for exact elapsed timeline time; the model then needs zone-aware values or instants.

Use `LocalTime` for wall-clock rules that intentionally do not point to the global timeline. When the question becomes “when did this really happen worldwide?”, add date and zone context.
