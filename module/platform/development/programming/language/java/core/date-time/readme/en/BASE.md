# Date and Time

This module builds a mental model for the Java Date-Time API by separating **local date/time**, **timeline instants**, **offsets/time zones**, **amounts/durations**, and **formatting/parsing**.

## Learning flow

1. the date-time mental model;
2. `LocalDate`;
3. `LocalTime`;
4. `LocalDateTime`;
5. `Instant`;
6. `ZoneOffset` and `ZoneId`;
7. `ZonedDateTime` and `OffsetDateTime`;
8. `Duration` and `Period`;
9. formatting and parsing;
10. arithmetic and comparison;
11. `Clock`;
12. legacy interop and pitfalls.

## Why learn this module?

Many time-related bugs come from choosing the wrong temporal type or treating local wall-clock time as an absolute instant. The goal is to choose the correct representation before worrying about display formatting.

## Expected outcome

The learner should model requirements in terms of timeline/local/zone semantics, perform arithmetic with the right meaning, and understand when to hand off to localization or legacy `Date/Calendar` interoperability.
