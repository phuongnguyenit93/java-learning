# Clock

Time-dependent code often contains an invisible dependency: **the system clock at the instant the method executes**. If business logic calls `Instant.now()` or `LocalDate.now()` directly in many places, tests become tied to real time and are harder to make deterministic.

`Clock` exists to turn the source of “now” into an object that can be supplied explicitly.

## <a id="clock-abstraction">Clock as the Current-Time Abstraction</a>

Without a clock dependency:

```java
boolean isExpired(Instant expiresAt) {
    return Instant.now().isAfter(expiresAt);
}
```

The method reads global system time directly. Its behavior depends on when the test happens to run.

With `Clock`:

```java
boolean isExpired(Instant expiresAt, Clock clock) {
    return Instant.now(clock).isAfter(expiresAt);
}
```

The business rule is unchanged, but the source of current time is now an explicit dependency.

### Useful Clock factories

```java
Clock utc = Clock.systemUTC();
Clock systemZone = Clock.systemDefaultZone();
Clock fixed = Clock.fixed(
        Instant.parse("2026-09-27T10:00:00Z"),
        ZoneOffset.UTC
);
Clock shifted = Clock.offset(utc, Duration.ofMinutes(5));
```

A `Clock` can provide both an instant and a zone:

```java
Instant now = clock.instant();
ZoneId zone = clock.getZone();
```

### InstantSource — when only an Instant source is needed

Since Java 17, `java.time` also provides `InstantSource`: a **narrower abstraction than `Clock`** whose job is simply to provide the current `Instant`.

```text
InstantSource
→ need to know "what is the current instant?"

Clock
→ also provides the current instant
→ additionally carries a ZoneId for APIs that need local/calendar context
```

`Clock` implements `InstantSource`, so a service that only needs timeline timestamps can depend on the narrower interface:

```java
boolean isExpired(Instant expiresAt, InstantSource source) {
    return !source.instant().isBefore(expiresAt);
}
```

Tests can still use a fixed source:

```java
InstantSource fixed = InstantSource.fixed(
        Instant.parse("2026-09-27T10:00:00Z")
);
```

Do not replace every `Clock` with `InstantSource`: when logic needs a `ZoneId`, `LocalDate.now(clock)`, or `ZonedDateTime.now(clock)`, `Clock` remains the better abstraction.

### Clock is not a stopwatch for elapsed time

`Clock`/`Instant.now()` model **wall-clock time**: they answer “which point on the timeline is it now?” A wall clock can be adjusted by the operating system, time synchronization, or the configured clock source.

When the goal is to measure **how long code took to execute** inside one JVM, Java provides `System.nanoTime()`:

```java
long start = System.nanoTime();

doWork();

long elapsedNanos = System.nanoTime() - start;
```

The absolute value returned by `nanoTime()` is not a timestamp, cannot be meaningfully converted to `Instant`, and has no calendar meaning. Only the **difference between readings in the same JVM** is meaningful for elapsed-time measurement.

```text
Clock / Instant.now()
→ "what point on the timeline is it now?"

System.nanoTime()
→ "how much time elapsed between two measurements in this JVM?"
```

These are different problems even though both involve the word “time.”

## <a id="fixed-clock-testing">Fixed Clock for Deterministic Tests</a>

Suppose a token expires at 10:05 UTC:

```java
Instant expiresAt = Instant.parse("2026-09-27T10:05:00Z");
```

Test the rule before expiry:

```java
Clock beforeExpiry = Clock.fixed(
        Instant.parse("2026-09-27T10:04:00Z"),
        ZoneOffset.UTC
);

assertFalse(isExpired(expiresAt, beforeExpiry));
```

Then after expiry:

```java
Clock afterExpiry = Clock.fixed(
        Instant.parse("2026-09-27T10:06:00Z"),
        ZoneOffset.UTC
);

assertTrue(isExpired(expiresAt, afterExpiry));
```

Neither test sleeps or depends on the real wall clock.

### Do not use Thread.sleep as a unit-test clock

Weak pattern:

```java
Thread.sleep(1_000);
assertTrue(...);
```

It makes tests slower and can introduce flakiness under scheduler or CI load. If the rule only needs controlled time, move the clock rather than waiting for real time to pass.

## <a id="clock-injection">Inject Clock Instead of Calling now Everywhere</a>

A service can receive `Clock` through its constructor:

```java
final class TokenService {
    private final Clock clock;

    TokenService(Clock clock) {
        this.clock = clock;
    }

    boolean isExpired(Instant expiresAt) {
        return !clock.instant().isBefore(expiresAt);
    }
}
```

Production wiring:

```java
TokenService service = new TokenService(Clock.systemUTC());
```

Test wiring:

```java
Clock fixed = Clock.fixed(
        Instant.parse("2026-09-27T10:00:00Z"),
        ZoneOffset.UTC
);

TokenService service = new TokenService(fixed);
```

### Where should Clock be injected?

Every method does not need its own `Clock` parameter. A common design is to inject one at a service/component boundary and use it internally. The goal is to avoid business logic reaching for an uncontrolled global time source.

### Clock is not a scheduler

`Clock` answers “what time does this source report now?” It does not run jobs, schedule callbacks, or guarantee wake-up timing. Scheduling is a separate responsibility.

The final chapter now combines production boundaries: legacy APIs, default-zone assumptions, and DST gaps/overlaps.
