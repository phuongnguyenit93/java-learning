# Locale and i18n Pitfalls

After learning each localization API individually, the most important practical skill is recognizing **where locale-sensitive behavior should not happen implicitly**. Many localization bugs are boundary mistakes: code fails to distinguish human-facing text from machine-facing data.

## <a id="default-locale">Default Locale and FORMAT/DISPLAY Categories</a>

The JVM has a default locale:

```java
Locale current = Locale.getDefault();
```

That is convenient for local desktop-style applications, but in a multi-user backend it can become a **hidden environmental dependency**.

Java exposes two important categories:

```text
Locale.Category.DISPLAY
→ locale used when displaying locale/language/country names

Locale.Category.FORMAT
→ locale used by formatting operations
```

```java
Locale displayLocale = Locale.getDefault(Locale.Category.DISPLAY);
Locale formatLocale = Locale.getDefault(Locale.Category.FORMAT);
```

The default can be changed:

```java
Locale.setDefault(Locale.Category.FORMAT, Locale.US);
```

but this changes JVM-wide state and can affect unrelated code. Request-based servers are usually safer when they pass the user locale explicitly:

```java
NumberFormat format = NumberFormat.getNumberInstance(userLocale);
```

rather than silently relying on:

```java
NumberFormat format = NumberFormat.getNumberInstance();
```

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

but they do **not all consult the same default**. `String.toLowerCase()` uses the JVM's general default Locale, while formatting APIs such as `NumberFormat` and localized `DateTimeFormatter` use the `FORMAT` default category. The common risk is the same: behavior is being driven by implicit JVM-wide state instead of an explicit application/user Locale.

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
Locale previousGeneral = Locale.getDefault();
Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
try {
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    // run a focused test
} finally {
    Locale.setDefault(previousGeneral);
    Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
    Locale.setDefault(Locale.Category.FORMAT, previousFormat);
}
```

`Locale.setDefault(Locale)` updates the JVM-wide general default and both category defaults, so a test that changes it must restore all three values if they may have differed beforehand. Because this is global mutable state, parallel tests can still interfere with each other; isolate such tests or prefer code whose locale dependency is explicit.

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

## <a id="localization-synthesis">Synthesis: Choose the Localization Boundary First</a>

After learning the individual APIs, decision-making should return to one end-to-end flow instead of starting from class names:

```text
1. Identify the canonical domain meaning
   → number, currency, instant, status, identifier, ...
        ↓
2. Decide whether the boundary is human-facing or machine-stable
        ↓
3. For human-facing output/input, choose Locale explicitly
   → user preference / request negotiation / product policy
        ↓
4. Choose the mechanism that owns the presentation problem
   → ResourceBundle / MessageFormat
   → NumberFormat / Currency
   → DateTimeFormatter + ZoneId
   → Collator / BreakIterator / Bidi when text behavior requires them
        ↓
5. Make fallback and default-Locale policy observable and intentional
        ↓
6. Keep machine-facing representations stable and locale-neutral
   → do not persist localized strings as domain values, identifiers, or protocol formats
```

When one step is unclear, the underlying problem is usually an **ownership/boundary decision**, not a missing formatter.

If one principle should remain after this module, it is this:

```text
Business meaning should stay stable.
Locale should change how people see that meaning, not what the meaning is.
```
