# Localization and Internationalization Mental Model

Software can be correct at the business level and still present information incorrectly to users in different languages and regions.

Consider one order whose canonical business data is:

```java
record Order(long id, BigDecimal total, Instant createdAt) {}

Order order = new Order(
        1001L,
        new BigDecimal("1234567.89"),
        Instant.parse("2026-09-27T08:30:00Z")
);
```

The numeric value `1234567.89` and the `Instant` should not change because a user selects Vietnamese instead of English. What changes is the **presentation**.

```text
vi-VN
→ 1.234.567,89
→ 27/09/2026
→ "Đơn hàng đã được tạo"

en-US
→ 1,234,567.89
→ 9/27/26
→ "Order created"
```

That is the problem space of internationalization and localization.

## <a id="i18n-vs-l10n">What Are Internationalization and Localization?</a>

The two terms are related but not identical:

- **internationalization (i18n)** means designing software so it can support different languages, regions, and presentation conventions without rewriting the business logic;
- **localization (l10n)** means supplying or selecting the concrete language and conventions for a particular locale, such as Vietnamese messages or US number formatting.

The abbreviations count the letters between the first and last characters:

```text
internationalization → i18n
localization          → l10n
```

### What goes wrong without internationalization?

A small application may start with hard-coded text:

```java
String message = "Order created";
String totalText = "$" + order.total();
```

Later, another language arrives and the code grows branches:

```java
if (language.equals("vi")) {
    message = "Đơn hàng đã được tạo";
} else {
    message = "Order created";
}
```

Repeated across a real application, this creates a predictable chain of problems:

```text
messages are scattered through source code
        ↓
each new language creates more branching
        ↓
translation requires source-code changes
        ↓
number/date/currency formatting still uses ad-hoc assumptions
        ↓
business logic and human presentation become coupled
```

Internationalization addresses the design problem: **keep canonical domain values separate from human-facing representation and apply locale-sensitive behavior only at the correct boundary**.

Localization then provides the concrete resources and conventions for a selected locale.

One distinction is especially important for beginners:

```text
translation
→ translating language content

localization
→ translation
  + number/date/currency formatting
  + text ordering/collation
  + other locale-sensitive presentation conventions
```

An application can translate every label into Vietnamese and still be localized incorrectly if it continues to display numbers, dates, or money using the wrong conventions.

### What pieces make up a practical localization system?

Beginners often meet individual classes without seeing how they fit together. A useful module-level pipeline is:

```text
1. Locale
→ language / script / region presentation context

2. Language tag
→ text representation used across HTTP/configuration/storage

3. Localized resources
→ messages/resources prepared for different locales

4. ResourceBundle
→ selects the appropriate resource and applies fallback

5. MessageFormat
→ inserts dynamic values into a translated message pattern

6. NumberFormat / DecimalFormat / Currency
→ presents numbers, percentages and money

7. DateTimeFormatter + ZoneId
→ presents date/time while keeping locale and time-zone responsibilities separate

8. Collator
→ compares and orders human text using language-sensitive rules

9. BreakIterator
→ finds character/word/sentence/line boundaries in natural-language text

10. Bidi
→ analyzes logical order and visual direction for LTR/RTL text
```

Not every application needs every piece. A machine-to-machine API may need almost no localization, while a multilingual user-facing application may use most of this pipeline.

## <a id="locale-sensitive-data">Locale-Sensitive Presentation vs Domain Data</a>

Not every value should change with `Locale`.

| Information | Canonical/domain representation | Locale-sensitive presentation |
| --- | --- | --- |
| numeric amount | `BigDecimal("1234567.89")` | `1.234.567,89` or `1,234,567.89` |
| currency | `Currency.getInstance("USD")` | `$`, `US$`, localized display name |
| point in time | `Instant` | localized date/time after choosing a zone |
| status | code/enum such as `PAID` | `Đã thanh toán` / `Paid` |
| message identity | `order.created` | translated message text |
| protocol identifier | stable machine string | normally locale-neutral |

A healthier domain model looks like:

```java
record Order(BigDecimal total, Currency currency, Instant createdAt) {}
```

Presentation receives separate context:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
```

and only then turns canonical values into text.

Storing formatted text as the primary domain value is usually a design smell:

```java
record Order(String total) {}

// "1.234.567,89 ₫"
```

That representation is harder to calculate with, harder to re-localize, and unsafe as a machine contract.

Use this mental model:

```text
canonical domain value
        ↓
stable business meaning
        ↓
presentation boundary
   + Locale
   + ZoneId when time-zone conversion is needed
   + Currency when money has a unit
        ↓
human-facing text
```

`Locale` is **not** a `ZoneId` and is **not** a `Currency`. These concepts can cooperate during presentation, but each has a separate responsibility.

## <a id="localization-boundaries">Where Localization Belongs</a>

Localization primarily belongs at boundaries where software communicates with people:

```text
UI / user-facing web response
→ translated messages
→ localized numbers, percentages, currencies and dates

email / report / invoice
→ presentation selected for the recipient

user-visible text sorting
→ locale-sensitive collation
```

Machine-facing data normally needs a stable contract instead:

```text
database numeric values
API machine contracts
cache keys
protocol identifiers
machine-parsed log fields
```

For example, do not normalize a technical key through an implicit user locale:

```java
String key = input.toLowerCase(); // depends on default Locale
```

For locale-neutral text rules, make the intention explicit:

```java
String key = input.toLowerCase(Locale.ROOT);
```

### Module roadmap

The rest of the module follows this path:

```text
separate domain meaning from presentation
        ↓
Locale
→ language / script / region context
        ↓
Language Tag
→ interoperable textual locale representation
        ↓
ResourceBundle
→ localized resource lookup
        ↓
MessageFormat
→ parameterized localized messages
        ↓
NumberFormat / DecimalFormat
→ numbers and percentages
        ↓
Currency
→ monetary unit plus locale-sensitive display
        ↓
Collator
→ user-facing text ordering
        ↓
DateTimeFormatter + Locale
→ localized date/time presentation
        ↓
ResourceBundle fallback
        ↓
default-locale and serialization pitfalls
        ↓
BreakIterator
→ character / word / sentence / line boundaries
        ↓
Bidi
→ logical order vs visual order for LTR / RTL text
```

The goal is not to memorize classes from `java.util` and `java.text`. The goal is to recognize **which values must remain locale-neutral, which boundaries are human-facing, and which Java API belongs at each presentation step**.
