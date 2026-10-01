# Collator and Locale-Aware Comparison

Sorting text looks simple until the order is meant for a human reader. `String.compareTo` compares strings lexicographically over their UTF-16 `char` code units; that deterministic order is not the same thing as the alphabetic order expected by every language.

`Collator` exists for **locale-sensitive text comparison and ordering**.

## <a id="collator-model">Collator Mental Model</a>

`Collator` can be thought of as a **`Comparator<String>` specialized for human language**. It applies locale-related collation rules and decides whether two strings compare as equal or which one should sort before the other in that context.

The main pieces are:

```text
Locale / collation rules
→ which linguistic ordering rules apply

strength
→ which differences are significant

decomposition
→ how equivalent/related Unicode representations are handled

compare(...)
→ performs the comparison

CollationKey
→ optimized comparison representation for repeated comparisons
```

Use `Collator` when **human-facing order** matters. For stable technical keys or protocol ordering, a locale-neutral order is usually more appropriate.

`String.compareTo` compares strings lexicographically over their UTF-16 `char` code units. That is useful for many machine-oriented tasks, but it does not model language-specific collation rules.

`Collator` adds locale context:

```java
Collator collator = Collator.getInstance(
        Locale.forLanguageTag("vi-VN")
);

int result = collator.compare("An", "Ân");
```

The mental model is:

```text
two user-facing strings
        ↓
Locale
        ↓
Collator
        ↓
language-sensitive comparison
```

Like a `Comparator`, the contract is based on the sign of the result:

```text
< 0 → first value sorts before the second
= 0 → equal at the current collation strength
> 0 → first value sorts after the second
```

The exact numeric result is not meaningful beyond its sign.

## <a id="collation-strength">Collation Strength and Decomposition</a>

Not every textual difference must have the same importance. `Collator` exposes **strength** levels that control which distinctions matter during comparison.

Common strengths are:

```text
PRIMARY
→ base-letter distinctions

SECONDARY
→ commonly includes accent/diacritic distinctions

TERTIARY
→ commonly includes case and finer distinctions

IDENTICAL
→ the strictest comparison level
```

```java
Collator collator = Collator.getInstance(Locale.US);
collator.setStrength(Collator.PRIMARY);
```

The exact linguistic meaning of each level is locale-dependent. Do not treat the summary above as a universal alphabet table for every language.

At a lower strength, two strings with different Unicode representations can compare as equivalent for collation purposes. That is useful for search or sorting where some differences should be ignored.

`Collator` also has **decomposition** settings. Unicode text can represent visually similar characters in composed or decomposed forms, for example a precomposed accented character versus a base character followed by a combining mark. Decomposition controls how those representations participate in comparison.

```java
collator.setDecomposition(Collator.NO_DECOMPOSITION);
collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
collator.setDecomposition(Collator.FULL_DECOMPOSITION);
```

Do not treat decomposition as another strength level. **Strength** decides which differences matter to comparison; **decomposition** decides how Unicode representations are prepared for that comparison.

The important lesson is that human text comparison can require both linguistic rules and Unicode normalization awareness; comparing raw character values is not always enough.

## <a id="collator-vs-string-order">Collator vs String Ordering</a>

Neither `Collator` nor `String.compareTo` is universally “better.” They serve different contracts.

| Requirement | Usually appropriate |
| --- | --- |
| sort localized names for a UI | `Collator` using the user's locale |
| sort localized product labels | `Collator` |
| stable technical key ordering | locale-neutral ordering |
| protocol token ordering | the protocol's explicit contract |
| database sorting | the database/schema collation |

Example user-facing sort:

```java
List<String> names = new ArrayList<>(
        List.of("An", "Ân", "Anh", "Ánh")
);

Collator collator = Collator.getInstance(
        Locale.forLanguageTag("vi-VN")
);

names.sort(collator);
```

Do not replace collation with a shortcut such as:

```java
left.toLowerCase(locale).compareTo(right.toLowerCase(locale));
```

Case conversion and collation are different problems. A language's ordering rules can involve more than letter case.

## <a id="sorting-user-text">Sorting User-Visible Text</a>

When sorting objects by a localized display name, a `Collator` can be used as the comparison strategy:

```java
record Product(String code, String displayName) {}

Collator collator = Collator.getInstance(locale);

products.sort(
        Comparator.comparing(Product::displayName, collator)
);
```

Three boundaries matter in production systems:

1. **The user's locale** should normally drive user-facing sort order, not the server machine's default locale.
2. If sorting and pagination happen in a database, the database collation is what actually determines row order. A Java `Collator` does not automatically make database ordering identical.
3. Collation output is presentation behavior, not canonical identity. Do not persist or compare business identifiers through user-locale sorting rules.

For repeated comparisons over a large set, `CollationKey` can precompute a comparison representation:

```java
CollationKey key = collator.getCollationKey(text);
```

That is an optimization to consider after the required comparison semantics are already correct.

The next chapter returns to the order example and date-time presentation. There, `Locale` controls **how a date/time is displayed**, while `ZoneId` remains responsible for **which local time corresponds to an instant**.
