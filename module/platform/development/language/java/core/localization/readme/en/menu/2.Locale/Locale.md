# Locale

Once domain values are separated from presentation, the application needs a way to describe **which language and regional conventions should guide that presentation**. In Java, that context is represented by `Locale`.

## <a id="locale-model">What Does Locale Model?</a>

`Locale` is a language/cultural context made from components such as:

```text
language
→ vi, en, ja

script
→ Latn, Cyrl, Hans, Hant

region/country
→ VN, US, GB

variant / extensions
→ additional specialized information
```

### In plain language: what is Locale and what is its role?

`Locale` is a **context/preference value object**. It does not translate text and it does not format data by itself.

Its role is to become an **input to APIs whose behavior depends on language or regional conventions**:

```text
Locale
  ├─→ ResourceBundle      → resource selection
  ├─→ NumberFormat        → number presentation
  ├─→ DateTimeFormatter   → localized names/pattern conventions
  ├─→ Currency display    → localized symbols/names
  └─→ Collator            → human text ordering
```

Think of `Locale` as **“the context that tells Java which conventions to apply”**, not as the conventions or translations themselves.

Examples:

```java
Locale vietnameseVietnam = Locale.forLanguageTag("vi-VN");
Locale englishUS = Locale.forLanguageTag("en-US");
Locale chineseTraditionalTaiwan = Locale.forLanguageTag("zh-Hant-TW");
```

Read `zh-Hant-TW` by role:

```text
zh   → Chinese language
Hant → Traditional Han script
TW   → Taiwan region
```

### Locale is not a physical location

`Locale` does not mean “the device is physically located in this country.” A user in Vietnam can prefer `en-US`; a server in Singapore can render `vi-VN` output.

### Where does an application's Locale come from?

A real application may choose a locale from several sources:

```text
a saved user preference
browser/HTTP language preferences
an application parameter or configuration
a product-defined fallback locale
the JVM default locale — only when that is intentionally part of the policy
```

There is no single source that is always correct. The important point is that the application should have an **explicit locale-selection policy** rather than allowing the machine environment to choose accidentally.

When an application needs to display the locale itself, methods such as `getDisplayLanguage(...)`, `getDisplayCountry(...)`, and `getDisplayName(...)` can produce human-facing names; the locale passed to those methods controls the language used for those names.

### Locale does not contain a time zone

Keep this boundary clear:

```text
Locale
→ language, localized names, formatting conventions

ZoneId
→ time-zone rules and offsets
```

`Locale.forLanguageTag("vi-VN")` does **not** imply `Asia/Ho_Chi_Minh`.

Displaying a point in time often needs both:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
```

## <a id="locale-construction">Constructing Locale Values</a>

When the input is already a language tag:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
```

When code owns the components, Java 21 provides `Locale.of(...)` factories:

```java
Locale vi = Locale.of("vi");
Locale viVN = Locale.of("vi", "VN");
```

For scripts, extensions, or stricter component assembly, use `Locale.Builder`:

```java
Locale locale = new Locale.Builder()
        .setLanguage("zh")
        .setScript("Hant")
        .setRegion("TW")
        .build();
```

The builder is useful when malformed subtags should fail with `IllformedLocaleException` instead of being treated as loosely parsed input.

Constants such as `Locale.US`, `Locale.UK`, and `Locale.JAPAN` are convenient, but application support should not be restricted to a hard-coded list merely because constants exist.

### Do not infer unrelated domain facts from Locale

Even a region-bearing locale is not a user profile:

```text
Locale.US
≠ user address
≠ nationality
≠ time zone
≠ mandatory transaction currency
```

Model country, currency, or zone explicitly when the domain requires them.

## <a id="default-locale">Default Locale and Categories</a>

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

## <a id="locale-equality">Locale Identity and Equality</a>

`Locale` is an immutable value object. Equality reflects the locale components that make up its identity.

```java
Locale a = Locale.forLanguageTag("vi-VN");
Locale b = Locale.of("vi", "VN");

System.out.println(a.equals(b)); // true
```

But “can use the same translation after fallback” is not the same thing as equality:

```java
Locale en = Locale.forLanguageTag("en");
Locale enUS = Locale.forLanguageTag("en-US");

System.out.println(en.equals(enUS)); // false
```

`en-US` carries a region and `en` does not. Resource fallback may eventually select a common bundle, but that is lookup behavior, not locale identity.

If a cache wants to group only by language, use `locale.getLanguage()` deliberately rather than assuming whole-locale equality has that meaning.

The next chapter moves from the in-memory Java object to the **language tag**, the textual form used across HTTP, configuration, databases, and other systems.
