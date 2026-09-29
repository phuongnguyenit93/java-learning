# Locale and i18n Pitfalls

After learning each localization API individually, the most important practical skill is recognizing **where locale-sensitive behavior should not happen implicitly**. Many localization bugs are boundary mistakes: code fails to distinguish human-facing text from machine-facing data.

## <a id="turkish-i">Case Conversion and the Turkish-I Problem</a>

Calls such as:

```java
text.toLowerCase()
text.toUpperCase()
```

use the default locale. That can be wrong when the string is a technical identifier rather than user-facing language.

A classic example is Turkish `I/i`, whose uppercase/lowercase relationships differ from English expectations.

Use this distinction:

```text
human-language text
→ case conversion may need the user's Locale

identifier / protocol token / technical key
→ usually needs Locale.ROOT or an explicit protocol rule
```

For a locale-neutral technical normalization:

```java
String normalizedKey = input.toLowerCase(Locale.ROOT);
```

`Locale.ROOT` represents a language/country-neutral locale suitable for operations that must not inherit a particular user's locale.

Lowercasing is also not a universal replacement for case-insensitive comparison. Unicode case mapping and a domain's identity rules can be more complex than ASCII-style assumptions.

## <a id="default-locale-production-risk">Default Locale as a Production Risk</a>

These calls look harmless:

```java
NumberFormat format = NumberFormat.getNumberInstance();
String lower = input.toLowerCase();
DateTimeFormatter formatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.SHORT);
```

but without explicit locale context their behavior can depend on the JVM environment or default locale category.

That creates failures such as:

```text
developer machine → tests pass
CI machine        → different output
production node A → default locale X
production node B → default locale Y
```

A multi-user backend is clearer when user-facing operations receive the locale explicitly:

```java
String render(Order order, Locale userLocale) { ... }
```

The default locale should be an **intentional fallback policy**, not an invisible dependency.

Tests can deliberately vary the default locale to expose accidental dependencies:

```java
Locale previous = Locale.getDefault();
try {
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    // run a focused test
} finally {
    Locale.setDefault(previous);
}
```

Because changing the default locale mutates JVM-wide state, parallel tests can interfere with each other. Isolate such tests or prefer code whose locale dependency is explicit.

## <a id="format-parse-roundtrip">Localized Formatting Is Not Stable Machine Serialization</a>

A dangerous architecture is:

```text
domain number/date
        ↓
format for a human
        ↓
store/send that text as the canonical value
        ↓
parse it again in another service
```

Localized formatting is optimized for **human readability**, not machine round trips.

For example, `1,234` can mean different things under different conventions, and a date such as `03/04/2026` is ambiguous without a known contract. Formatters can also round values or omit fields that the UI does not need.

Prefer a clear split:

```text
machine boundary
→ explicit numeric representation
→ ISO date/time where appropriate
→ explicit currency code

human boundary
→ localized NumberFormat / DateTimeFormatter / messages
```

Even formatting and parsing with the same formatter does not guarantee preservation of the original object if the presentation intentionally drops precision or information.

## <a id="translation-key-design">Stable Translation Keys and Complete Messages</a>

Translation keys should be stable semantic identifiers rather than copies of the current English wording.

Prefer:

```properties
order.created=Order {0} was created.
order.cancelled=Order {0} was cancelled.
```

over a key whose identity is tied to one sentence spelling:

```properties
Order_was_created=Order was created
```

If the English wording changes later, `order.created` still represents the same application meaning.

### Do not build translatable sentences from fragments

Avoid:

```text
"Order " + id + " was " + statusText
```

because the translator cannot rearrange the full sentence naturally.

Prefer a complete parameterized resource:

```properties
order.status=Order {0} is {1}.
```

If different statuses require materially different grammar across languages, separate semantic message keys can be better than forcing every language into one English-shaped template.

### Localization review checklist

```text
[ ] Are formatted strings being stored as canonical domain values?
[ ] Is Locale passed explicitly at user-facing boundaries?
[ ] Is Locale being confused with ZoneId, Currency, country, or user location?
[ ] Do technical identifiers call toLowerCase()/toUpperCase() with an implicit default locale?
[ ] Does machine serialization accidentally use localized NumberFormat/date formatting?
[ ] Are translatable sentences assembled from fragments?
[ ] Are MessageFormat apostrophes and placeholders tested?
[ ] Are ResourceBundle missing keys and fallback behavior validated?
[ ] Does user-visible sorting need a Collator?
[ ] Is Java collation incorrectly assumed to match database collation?
```

If one principle should remain after this module, it is this:

```text
Business meaning should stay stable.
Locale should change how people see that meaning, not what the meaning is.
```
