# Instant

After understanding that `LocalDateTime` is not a global timeline point, the next question is: **how does Java represent one exact moment that can be compared across systems?** `Instant` is the central type for that job.

## <a id="instant-model">Instant Model</a>

`Instant` represents **one point on the UTC timeline**. It does not primarily model a human calendar view such as `2026-10-05 09:00 Asia/Ho_Chi_Minh`; it models the event's absolute timeline position.

```java
Instant now = Instant.now();
Instant explicit = Instant.parse("2026-10-05T02:00:00Z");
```

The `Z` suffix in ISO-8601 denotes offset `+00:00`, or UTC.

### Why does Instant matter in distributed systems?

Two servers can display the same event using different wall clocks:

```text
Server A displays: 09:00 in Vietnam
Server B displays: 04:00 in Paris
```

Those local representations may still point to **the same instant**. For audit data, event ordering, message timestamps, and cross-system communication, putting values on one shared timeline removes local-time ambiguity.

### Instant is not “LocalDateTime in UTC”

Do not model `Instant` mentally as a `LocalDateTime` that happens to use UTC. `Instant` does not own year/month/day/hour as its primary domain fields. Calendar fields appear only after the instant is projected through a zone.

```java
Instant instant = Instant.parse("2026-10-05T02:00:00Z");

ZonedDateTime vietnamView = instant.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
ZonedDateTime parisView = instant.atZone(ZoneId.of("Europe/Paris"));
```

One instant can have many local calendar views.

### Advanced boundary — Java Time-Scale and leap seconds

A beginner can keep the simpler model: `Instant` is one point on a global UTC-oriented timeline. At the deeper specification level, Java defines a **Java Time-Scale** for its date-time classes; it models each calendar day with 86,400 subdivisions and defines its own relationship to a **leap second** — a special civil-time adjustment occasionally introduced to keep civil time close to Earth's rotation.

For ordinary applications, remember:

```text
Instant
→ excellent for event timestamps, ordering, and persistence boundaries

Instant
→ do not assume it is an astronomical/leap-second measurement model
  unless the domain explicitly requires one
```

## <a id="epoch-time">Epoch Time</a>

One way to represent timeline position is to count from an **epoch**. Java's `Instant` uses `1970-01-01T00:00:00Z` as its standard epoch origin.

```java
Instant epoch = Instant.EPOCH;
Instant fromSeconds = Instant.ofEpochSecond(1_000_000);
Instant fromMillis = Instant.ofEpochMilli(1_000_000);
```

You can read the value as epoch seconds plus a nanosecond fraction:

```java
long seconds = instant.getEpochSecond();
int nanos = instant.getNano();
long millis = instant.toEpochMilli();
```

`toEpochMilli()` converts to millisecond resolution and can discard precision finer than a millisecond.

`Instant` also has a very large supported range: from `Instant.MIN` around year `-1000000000` through `Instant.MAX` around year `+1000000000`. This is mostly a technical boundary; ordinary business applications operate far inside it.

### Raw epoch numbers need an explicit unit

A value such as:

```text
1728093600
```

does not tell you whether it means seconds, milliseconds, microseconds, or nanoseconds. Confusing those units can move a date by thousands of years. JSON, database, and messaging schemas should state the unit explicitly, or use a well-defined textual representation such as ISO-8601.

### Epoch representation does not remove the need for zones

An epoch value identifies an instant, but a user still needs a zone to see a local calendar representation:

```java
Instant stored = Instant.parse("2026-10-05T02:00:00Z");
ZoneId userZone = ZoneId.of("Asia/Ho_Chi_Minh");

LocalDateTime displayedLocal = LocalDateTime.ofInstant(stored, userZone);
```

Timeline storage and presentation-zone selection are separate responsibilities.

## <a id="instant-use-cases">Instant Use Cases and Boundaries</a>

`Instant` is commonly appropriate for values such as:

```text
createdAt / updatedAt for events that already occurred
requestReceivedAt
messageProducedAt
tokenIssuedAt / expiresAt when the rule is timeline-based
audit log timestamps
```

```java
record AuditEvent(String action, Instant occurredAt) {}

AuditEvent event = new AuditEvent("PAYMENT_CONFIRMED", Instant.now());
```

### When Instant is not the whole model

A future meeting such as “09:00 on October 5 in Europe/Paris” expresses intent in **local civil time plus region rules**. If the application stores only the initially resolved instant, it may lose region context that the domain needs for display, rescheduling, or reacting to future rule changes.

Ask:

```text
Has the event already occurred and only timeline position matters?
→ Instant is often sufficient

Is this a future schedule tied to a named place?
→ preserve local date-time + ZoneId/ZonedDateTime as the domain requires
```

### Comparing instants

```java
Instant a = Instant.parse("2026-10-05T02:00:00Z");
Instant b = Instant.parse("2026-10-05T03:00:00Z");

a.isBefore(b); // true
a.isAfter(b);  // false
```

Both values are already on the same global timeline, so comparison does not require guessing a zone.

The next chapter covers the bridge back to local civil time: **how is one concrete UTC offset different from a named zone whose rules can change by date?**
