# Currency

The number `100` does not tell us whether the value means 100 VND, 100 USD, or 100 EUR. Currency is the **unit of a monetary amount**; locale is presentation context. They interact but must not be treated as the same concept.

## <a id="currency-model">Currency Model</a>

`java.util.Currency` represents currency metadata known to the JDK, commonly identified by an ISO 4217 code:

```java
Currency usd = Currency.getInstance("USD");
Currency vnd = Currency.getInstance("VND");
```

### Why use Currency instead of just `"USD"` or `"$"`?

A string is only text. `Currency` gives Java a meaningful object representing the **identity and metadata of a monetary unit**.

Important pieces of that metadata include:

```text
currency code      → USD
numeric code       → corresponding ISO numeric code
symbol             → $, US$, ... depending on display locale
display name       → localized currency name
default fraction digits
                    → default fractional-digit metadata for the currency
```

A symbol is especially unsuitable as identity because the same symbol, such as `$`, can be associated with multiple currencies. Domain data should keep an explicit currency code/object rather than relying on a display symbol.

It exposes metadata such as:

```java
usd.getCurrencyCode();
usd.getNumericCode();
usd.getDefaultFractionDigits();
usd.getSymbol(Locale.US);
usd.getDisplayName(Locale.US);
```

`getDefaultFractionDigits()` is currency metadata, not a complete accounting rule for every business case. Monetary calculations still require explicit scale and rounding policies.

`Currency` does **not** contain an amount:

```text
Currency    → USD
BigDecimal  → 125.50
money value → must preserve both amount and unit
```

Java Core does not provide a built-in `Money` domain value type in `java.base` that solves all monetary modeling concerns.

## <a id="currency-vs-locale">Currency Is Not Locale</a>

Java can obtain the currency associated with a locale's region:

```java
Currency currency = Currency.getInstance(Locale.US);
```

That does not mean every user with `en-US` preferences transacts in USD.

A Vietnamese UI can display a USD transaction:

```java
Locale displayLocale = Locale.forLanguageTag("vi-VN");
Currency transactionCurrency = Currency.getInstance("USD");
```

The two values answer different questions:

```text
displayLocale
→ which human presentation conventions should be used?

transactionCurrency
→ which monetary unit does this amount represent?
```

When currency matters to the business, store/model it explicitly instead of reconstructing it from locale at display time.

## <a id="currency-format">Currency NumberFormat</a>

Create a currency formatter from the display locale, then set the transaction currency explicitly when necessary:

```java
NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
format.setCurrency(Currency.getInstance("USD"));

String text = format.format(new BigDecimal("1234.50"));
```

The two inputs have separate jobs:

```text
Locale.US
→ separators, symbol placement, localized formatting conventions

Currency.getInstance("EUR")
→ the monetary unit to display
```

Formatting EUR for a US-English user is perfectly valid:

```java
NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
format.setCurrency(Currency.getInstance("EUR"));
System.out.println(format.format(new BigDecimal("25.00")));
```

Avoid manual symbol concatenation such as `"$" + amount`; symbol choice, placement, spacing, and ambiguity can all be locale-sensitive.

## <a id="money-boundary">Formatting vs Monetary Domain Modeling</a>

Localization owns presentation. It does not answer domain questions such as:

```text
can USD and EUR amounts be added?
which exchange rate and timestamp apply?
how should tax be rounded?
what scale should persistence use?
how should remainder be allocated when money is divided?
```

A minimal domain value might be:

```java
record Money(BigDecimal amount, Currency currency) {}
```

and localization can render it later:

```java
String display(Money money, Locale locale) {
    NumberFormat format = NumberFormat.getCurrencyInstance(locale);
    format.setCurrency(money.currency());
    return format.format(money.amount());
}
```

This preserves the module's central boundary: **the domain owns meaning; locale controls human presentation**.

The next chapter applies the same idea to text ordering. The order humans expect from an alphabet is not necessarily the raw order produced by `String.compareTo`, so Java provides `Collator`.
