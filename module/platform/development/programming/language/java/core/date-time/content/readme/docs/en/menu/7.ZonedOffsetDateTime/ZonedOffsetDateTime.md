# OffsetDateTime, ZonedDateTime, and Zone Conversion

Once local fields, offsets, and zone rules are separate in the mental model, Java provides two useful value types that carry some of that context together: `ZonedDateTime` and `OffsetDateTime`.

## <a id="zoned-date-time">ZonedDateTime — Local Fields + ZoneId + Resolved Offset</a>

`ZonedDateTime` represents a date-time associated with **a `ZoneId`**. In application scheduling that `ZoneId` is often a named region such as `Asia/Ho_Chi_Minh` or `Europe/Paris`, but the API can also use a fixed-offset zone.

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime meeting = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, zone);
```

Conceptually:

```text
LocalDateTime fields
        +
ZoneId
        ↓ resolve through ZoneRules
valid ZoneOffset for that context
        ↓
ZonedDateTime
```

A `ZonedDateTime` can expose all of these views:

```java
LocalDateTime local = meeting.toLocalDateTime();
ZoneId zoneId = meeting.getZone();
ZoneOffset offset = meeting.getOffset();
Instant instant = meeting.toInstant();
```

It is a natural fit when the domain says “09:00 in Europe/Paris” or otherwise needs to preserve zone context together with local calendar fields. When the `ZoneId` is region-based, the value can also use rules that vary over time.

## <a id="offset-date-time">OffsetDateTime — Local Fields + One Concrete Offset</a>

`OffsetDateTime` stores local fields plus **one `ZoneOffset`**, but no named region rule set.

```java
OffsetDateTime value = OffsetDateTime.of(
        2026, 10, 5,
        9, 0, 0, 0,
        ZoneOffset.ofHours(7)
);
```

That is still enough to calculate an instant:

```java
Instant instant = value.toInstant();
```

But the value cannot prove that the original region was `Asia/Ho_Chi_Minh`.

### Choosing between ZonedDateTime and OffsetDateTime

```text
Does the domain need a named region and its rules over time?
→ ZonedDateTime

Does a boundary provide only local fields + a concrete offset?
→ OffsetDateTime

Does only the global timeline point matter?
→ consider Instant
```

Offset-carrying API timestamps are often excellent interchange values, while future scheduling may still need a separate `ZoneId` when regional rule semantics matter.

## <a id="same-instant-vs-same-local">Same Instant vs Same Local Date-Time</a>

Two zoned values can show different wall-clock times while representing the same instant:

```java
ZonedDateTime vietnam = ZonedDateTime.of(
        2026, 10, 5, 9, 0, 0, 0,
        ZoneId.of("Asia/Ho_Chi_Minh")
);

ZonedDateTime parisSameInstant = vietnam.withZoneSameInstant(
        ZoneId.of("Europe/Paris")
);

vietnam.toInstant().equals(parisSameInstant.toInstant()); // true
```

Conversely, two values can preserve the same local clock reading:

```text
09:00 Vietnam
09:00 Paris
```

while referring to different instants.

```text
same instant
→ preserve timeline position
→ wall-clock fields change when zone changes

same local
→ preserve calendar/clock fields
→ timeline position may change
```

That distinction is why a request to “convert the zone” must specify what should be preserved.

## <a id="zone-conversion">Intentional Zone Conversion</a>

### Preserve the instant

```java
ZonedDateTime source = ZonedDateTime.of(
        2026, 10, 5, 9, 0, 0, 0,
        ZoneId.of("Asia/Ho_Chi_Minh")
);

ZonedDateTime parisView = source.withZoneSameInstant(
        ZoneId.of("Europe/Paris")
);
```

Use this when displaying **the same event** to a user in another zone.

### Preserve local fields

```java
ZonedDateTime reinterpreted = source.withZoneSameLocal(
        ZoneId.of("Europe/Paris")
);
```

This keeps the local date/time as closely as the target rules allow and therefore normally changes the instant. It is not another view of the same event; it is a reinterpretation of a local schedule under different regional rules.

### Start from an Instant

```java
Instant eventTime = Instant.parse("2026-10-05T02:00:00Z");

ZonedDateTime vietnamView = eventTime.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
ZonedDateTime parisView = eventTime.atZone(ZoneId.of("Europe/Paris"));
```

Both values preserve the same event identity.

### OffsetDateTime has same-instant and same-local conversions too

The same mental model applies to `OffsetDateTime`:

```java
OffsetDateTime original = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");

OffsetDateTime sameInstant = original.withOffsetSameInstant(
        ZoneOffset.ofHours(2)
);
// 2026-10-05T04:00+02:00 → same instant

OffsetDateTime sameLocal = original.withOffsetSameLocal(
        ZoneOffset.ofHours(2)
);
// 2026-10-05T09:00+02:00 → same local fields, different instant
```

```text
withOffsetSameInstant
→ preserve the timeline point and change wall-clock fields for the new offset

withOffsetSameLocal
→ preserve local fields and reinterpret them under the new offset
```

Because `OffsetDateTime` carries no regional rule set, this is conversion between concrete offsets only; it does not perform DST rule lookup like a region-based `ZoneId` does.

Resolving local fields around DST gaps and overlaps adds another layer of rules; the Arithmetic / Comparison chapter covers those cases explicitly. First, the next chapter separates two different meanings of an “amount of time”: `Duration` and `Period`.
