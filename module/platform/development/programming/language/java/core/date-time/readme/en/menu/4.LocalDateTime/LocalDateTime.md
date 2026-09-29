# LocalDateTime

`LocalDateTime` combines `LocalDate` and `LocalTime`: it knows **which date** and **what local clock time**, but deliberately contains no `ZoneId` or `ZoneOffset`.

That makes it useful, but it also makes it one of the easiest temporal types to misuse as a global timestamp.

## <a id="local-date-time-model">LocalDateTime Model</a>

```java
LocalDateTime meeting = LocalDateTime.of(2026, 10, 5, 9, 0);
```

The value says:

```text
date = 2026-10-05
time = 09:00
```

It does **not** say:

```text
09:00 where?
what offset from UTC?
which instant on the global timeline?
```

You can build it by composing the previous two concepts:

```java
LocalDate date = LocalDate.of(2026, 10, 5);
LocalTime time = LocalTime.of(9, 0);

LocalDateTime a = LocalDateTime.of(date, time);
LocalDateTime b = date.atTime(time);
```

Arithmetic follows the same immutable value style:

```java
LocalDateTime rescheduled = meeting.plusDays(1).withHour(10);
```

## <a id="local-date-time-ambiguity">Why LocalDateTime Is Not an Instant</a>

This is the central distinction of the chapter.

The local value:

```text
2026-10-05T09:00
```

can map to different global instants under different zones:

```java
LocalDateTime local = LocalDateTime.of(2026, 10, 5, 9, 0);

Instant vietnam = local
        .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
        .toInstant();

Instant paris = local
        .atZone(ZoneId.of("Europe/Paris"))
        .toInstant();
```

The local fields are identical, but the region rules resolve them onto different points of the global timeline.

```text
LocalDateTime
    + ZoneId
        ↓
resolve local fields through zone rules
        ↓
ZonedDateTime
        ↓
Instant
```

### DST makes the ambiguity visible

In regions with daylight-saving transitions, a local date-time can fall into one of three shapes:

```text
normal
→ exactly one valid offset

gap
→ the local clock jumps forward
→ some local times do not exist

overlap
→ the clock moves backward
→ some local times occur twice under two offsets
```

Because `LocalDateTime` carries no zone rules, it cannot resolve those cases by itself. Later chapters cover the actual zone-resolution behavior.

### Common mistake: createdAt as LocalDateTime

```java
class Order {
    LocalDateTime createdAt;
}
```

If `createdAt` means an event that actually occurred in a distributed system, a zone-less local value may lose the information needed to compare events across machines. `Instant` is usually a better semantic fit for that kind of machine-oriented timestamp.

## <a id="local-date-time-use-cases">Appropriate LocalDateTime Use Cases</a>

`LocalDateTime` is correct when the domain genuinely owns **local calendar fields** while no timeline mapping exists yet, or when the local civil value itself is what the business cares about.

### 1. User input before a location is selected

```java
LocalDateTime requestedSlot = LocalDateTime.of(2026, 10, 5, 9, 0);
```

Later, once the region is known:

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime scheduled = requestedSlot.atZone(zone);
```

### 2. A domain rule expressed in local civil time

**Local civil time** means the calendar date and wall-clock time people in a place use for everyday/business purposes before that value is mapped to one global instant. A rule such as “close the accounting day at 23:00 on the last day of the month” is naturally expressed in local calendar fields. The owning zone may be supplied by a separate business configuration boundary.

### 3. A database column whose semantics are local time

If a column really means “timestamp without time zone,” `LocalDateTime` can be a natural mapping. The application must still avoid treating that column as an already-resolved global instant.

### When is it the wrong type?

Do not choose `LocalDateTime` simply because an input string looks like:

```text
2026-10-05 09:00:00
```

**Text shape does not define temporal semantics.** Ask what the data means.

```text
Audit timestamp?             → usually Instant
Meeting in a real region?    → ZonedDateTime or local + ZoneId
Date + time before zone?     → LocalDateTime
```

The next chapter moves to `Instant`, the type used when the model finally needs **one unambiguous point on the global timeline**.
