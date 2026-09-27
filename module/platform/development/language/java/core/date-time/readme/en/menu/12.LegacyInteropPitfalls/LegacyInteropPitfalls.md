# Legacy Interop and Date-Time Pitfalls

`java.time` fixes many design problems in older date-time APIs, but real systems still encounter `java.util.Date`, `Calendar`, JDBC types, database columns, and machine defaults. Modern code therefore needs to interoperate without importing legacy mental models into the new design.

## <a id="legacy-date-calendar">Legacy Date and Calendar Mental Model</a>

### java.util.Date

The name `Date` is misleading to modern readers: `java.util.Date` is not a calendar-date type like `LocalDate`. It primarily represents a point in time using milliseconds from the epoch and comes from an older mutable API design.

```java
Date legacy = new Date();
```

Many calendar-style getters/setters on `Date` have long been deprecated. New application code should generally prefer `java.time` types whose semantics are explicit.

### Calendar

`Calendar` combines calendar fields, zone information, and mutable arithmetic:

```java
Calendar calendar = Calendar.getInstance();
calendar.add(Calendar.DAY_OF_MONTH, 1);
```

The operation mutates the object, unlike the immutable value model used by `java.time`.

Legacy types still appear at integration boundaries, but their presence does not make them the right default for new domain code.

### SimpleDateFormat — a mutable, non-thread-safe legacy formatter

Older code frequently contains `java.text.SimpleDateFormat`:

```java
SimpleDateFormat legacyFormatter = new SimpleDateFormat("dd/MM/yyyy");
```

Unlike `DateTimeFormatter`, `SimpleDateFormat` is **mutable and not thread-safe**. Sharing one global/static instance across multiple threads can therefore create race conditions and unpredictable parse/format results.

```text
SimpleDateFormat
→ legacy formatter
→ mutable
→ should not be shared across threads without appropriate synchronization

DateTimeFormatter
→ modern java.time formatter
→ immutable + thread-safe
```

For new code, prefer `DateTimeFormatter`; keep `SimpleDateFormat` only at legacy boundaries where an old API requires it.

## <a id="legacy-conversion">Intentional Legacy Conversion</a>

### Date ↔ Instant

`Date` and `Instant` can both represent a timeline point, so conversion is direct:

```java
Date legacy = new Date();
Instant instant = legacy.toInstant();

Date back = Date.from(instant);
```

A useful boundary strategy is to convert into modern types early, keep business logic in `java.time`, and convert back only when a legacy API requires it.

### Calendar → ZonedDateTime

```java
Calendar calendar = Calendar.getInstance();

ZonedDateTime modern = calendar.toInstant()
        .atZone(calendar.getTimeZone().toZoneId());
```

Both the timeline point and the calendar's zone are used so the modern zoned view preserves the relevant context.

### `java.sql.Date`, `Time`, and `Timestamp`

**JDBC (Java Database Connectivity)** is Java's API for working with relational databases. Older JDBC code often exposes three `java.sql.*` wrappers that are easy to confuse with `java.time` types:

```text
java.sql.Date
→ SQL DATE-style value
→ natural modern counterpart: LocalDate

java.sql.Time
→ SQL TIME-style value
→ natural modern counterpart: LocalTime

java.sql.Timestamp
→ SQL TIMESTAMP-style wrapper with fractional seconds
→ provides conversion APIs for LocalDateTime and Instant
```

Java provides direct conversions:

In the snippet below, `sqlDate`, `sqlTime`, and `timestamp` are assumed to be **legacy JDBC values already supplied by an older API/database layer**; the code intentionally focuses only on conversion into and out of `java.time`.

```java
LocalDate date = sqlDate.toLocalDate();
java.sql.Date sqlDateAgain = java.sql.Date.valueOf(date);

LocalTime time = sqlTime.toLocalTime();
java.sql.Time sqlTimeAgain = java.sql.Time.valueOf(time);

LocalDateTime localDateTime = timestamp.toLocalDateTime();
java.sql.Timestamp timestampFromLocal = java.sql.Timestamp.valueOf(localDateTime);

Instant instant = timestamp.toInstant();
java.sql.Timestamp timestampFromInstant = java.sql.Timestamp.from(instant);
```

These legacy conversions are **not perfectly symmetric**, and several traps matter:

```text
java.sql.Date.toInstant()
→ unsupported; throws UnsupportedOperationException

java.sql.Time.toInstant()
→ unsupported; throws UnsupportedOperationException

java.sql.Time.valueOf(LocalTime)
→ keeps only hour/minute/second
→ the LocalTime nanosecond fraction is lost

Timestamp.from(Instant) / timestamp.toInstant()
→ timeline-oriented conversion

Timestamp.valueOf(LocalDateTime) / timestamp.toLocalDateTime()
→ legacy local-date-time interpretation
→ mapping between the legacy millisecond value and local fields can depend on the default time zone
```

If the domain owns an `Instant`, prefer an intentional `Timestamp ↔ Instant` boundary. If the schema truly owns local date-time semantics, use `Timestamp ↔ LocalDateTime` deliberately and control default-zone assumptions in the JDBC/database stack.

Do not infer that every database `TIMESTAMP` column has the same semantics merely because `Timestamp` and `Instant` can both participate in timeline conversions. SQL type semantics, time-zone handling, and precision depend on the database/schema/driver contract and must be designed explicitly.

### Modern JDBC can work directly with java.time

Since JDBC 4.2, several `java.time` types have standard direct mappings through `setObject` / `getObject`, so new code **does not have to detour through `java.sql.Date/Time/Timestamp`** merely to access a database.

For a driver/database supporting the corresponding mappings:

In this snippet, `preparedStatement` is an application-created `PreparedStatement` used to send parameters to the database, while `resultSet` is the `ResultSet` returned by a query. They are JDBC boundary objects, not date-time types.

```java
LocalDate invoiceDate = LocalDate.of(2026, 9, 27);
preparedStatement.setObject(1, invoiceDate);

LocalDate loadedDate = resultSet.getObject("invoice_date", LocalDate.class);

OffsetDateTime occurredAt = OffsetDateTime.parse("2026-09-27T17:30:00+07:00");
preparedStatement.setObject(2, occurredAt);

OffsetDateTime loadedOccurredAt = resultSet.getObject(
        "occurred_at",
        OffsetDateTime.class
);
```

`LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetTime`, and `OffsetDateTime` have standard JDBC 4.2 mappings. The actual database type, driver capability, and time-zone semantics still depend on the schema/provider. In particular, do not assume every driver offers the same direct `Instant` mapping just because the application domain uses `Instant`.

### Do not convert through text when a direct API exists

Avoid unnecessary paths such as:

```text
Date → format String → parse LocalDateTime
```

That injects formatting, locale, and possibly default-zone assumptions into a conversion that can be expressed directly.

## <a id="system-default-zone-risk">System Default Time-Zone Risk</a>

Calls such as:

```java
ZoneId.systemDefault();
LocalDate.now();
ZonedDateTime.now();
```

can depend on machine configuration when no explicit clock/zone is supplied.

A common deployment mismatch is:

```text
developer laptop → Asia/Ho_Chi_Minh
CI               → UTC
production       → UTC or another region
```

The same instant near midnight can belong to different `LocalDate` values in different zones.

### Hidden-zone conversion

```java
LocalDateTime local = ...;
Instant instant = local.atZone(ZoneId.systemDefault()).toInstant();
```

If `local` belongs to a known business zone, using the machine default is an undocumented assumption. Supply the correct `ZoneId` from domain/configuration instead.

## <a id="dst-gap-overlap">DST Gaps and Overlaps</a>

Regions using daylight saving time can have special local-clock transitions.

### Gap — a range of local times does not exist

When the clock jumps forward, for example from 02:00 to 03:00, the skipped local times do not exist in that region on that date.

Convenience APIs such as `LocalDateTime.atZone(zone)` resolve a gap according to `ZonedDateTime` rules by moving the local time forward by the gap length.

For important user scheduling input, the application may prefer to detect the gap rather than silently accept the default adjustment:

```java
LocalDateTime local = ...;
ZoneId zone = ZoneId.of("Europe/Paris");

List<ZoneOffset> validOffsets = zone.getRules().getValidOffsets(local);

if (validOffsets.isEmpty()) {
    // local time falls in a gap
}
```

### Overlap — one local time occurs twice

When the clock moves backward, one local clock reading can be valid under two offsets.

```java
List<ZoneOffset> validOffsets = zone.getRules().getValidOffsets(local);

if (validOffsets.size() == 2) {
    // ambiguous local time
}
```

Normal local-to-zone construction follows concrete resolution rules:

```text
normal
→ 1 valid offset → use it

gap
→ 0 valid offsets
→ move the local date-time forward by the length of the gap

overlap
→ 2 valid offsets
→ choose the earlier offset on the local timeline by default
  (typically the summer/before-transition offset)
```

If the application needs the other occurrence during an overlap, `withLaterOffsetAtOverlap()` selects the later offset; `withEarlierOffsetAtOverlap()` selects the earlier one explicitly.

When code needs to inspect the transition itself rather than only count valid offsets, `ZoneRules.getTransition(localDateTime)` returns the corresponding `ZoneOffsetTransition`; `nextTransition(instant)` and `previousTransition(instant)` find transitions around a timeline point.

The key lesson is not memorizing one DST date; it is understanding this relationship:

```text
LocalDateTime + ZoneId
does not always map 1:1 to Instant
```

## <a id="timestamp-storage-boundary">Choose Storage Semantics Deliberately</a>

There is no universal rule saying “all date-time data must be stored as UTC” for every domain. A better rule is: **store enough information to reconstruct the semantics the business actually owns**.

### Events that already happened

Examples:

```text
paymentCapturedAt
requestReceivedAt
auditEventAt
```

These usually need a timeline point, so `Instant`/UTC-oriented storage is a natural fit.

### Future schedules tied to a place

For a requirement such as:

```text
“09:00 on October 5 in Europe/Paris”
```

the domain may need to preserve:

```text
local date-time
+ ZoneId
```

because the regional rule set is part of the scheduling intent. Storing only the initially resolved instant or offset can lose context needed for rescheduling, display, or future rule updates.

### Local business dates and times

Birthdays, invoice dates, and opening times should not be forced into instants when the domain has no timeline semantics.

### Checklist before choosing a column/type

```text
1. Is this a calendar value or a timeline event?
2. Does the domain need a named region?
3. Is the offset original data or merely a resolved result?
4. Is this a future schedule or an event that already happened?
5. What precision does the storage system preserve?
6. What information must be reconstructed when the value is read back?
```

If those questions are answered first, choosing among `LocalDate`, `LocalDateTime`, `Instant`, `OffsetDateTime`, and `ZonedDateTime` becomes a domain decision rather than a vague convention.

The module's final mental model is:

```text
Understand temporal meaning first
        ↓
choose a type that preserves exactly that information
        ↓
add zone/offset only when the domain needs it
        ↓
choose Duration vs Period from semantics
        ↓
format/parse at boundaries
        ↓
inject Clock for “now”
        ↓
control default zones, DST, and legacy conversions explicitly
```
