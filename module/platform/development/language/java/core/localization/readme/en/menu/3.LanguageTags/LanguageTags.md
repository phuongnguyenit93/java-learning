# Language Tags

`Locale` is a Java object, but locale information frequently crosses HTTP, configuration, URLs, or persistent storage. An interoperable text representation is therefore necessary. Java uses **IETF BCP 47 language tags** for this role.

## <a id="bcp47-language-tag">BCP 47 Language Tag Mental Model</a>

A tag combines subtags, usually from general to more specific:

```text
language[-Script][-REGION][-variant...][-extensions...]
```

Examples:

```text
vi-VN
en-US
en-GB
zh-Hans-CN
zh-Hant-TW
```

In short, **a language tag is the standardized text form that describes locale information**, while `Locale` is Java's object representation of that information.

```text
"vi-VN"
→ interoperable language tag

Locale.forLanguageTag("vi-VN")
→ Java object used with APIs
```

Language tags exist because a Java object cannot be placed directly into an HTTP header, URL, or configuration file. A common textual standard lets browsers, servers, and services exchange the same locale context.

This is not an arbitrary hyphen-separated string. Each subtag has a standardized role so different systems can exchange locale context consistently.

```text
browser / HTTP / config / database
        ↓ language tag
Java application
        ↓ Locale
formatter / bundle / collator
```

## <a id="for-language-tag">Locale.forLanguageTag and toLanguageTag</a>

Convert text to a `Locale`:

```java
Locale viVN = Locale.forLanguageTag("vi-VN");
```

Convert back to the language-tag form:

```java
String tag = viVN.toLanguageTag();
System.out.println(tag); // vi-VN
```

This is preferable to inventing a transport format such as:

```java
locale.getLanguage() + "_" + locale.getCountry()
```

BCP 47 uses `-`, while traditional resource-bundle suffixes commonly use `_`:

```text
language tag             → vi-VN
ResourceBundle file name → Messages_vi_VN.properties
```

Do not treat these conventions as interchangeable.

### What if the client supplies several preferred locales?

Real clients do not always say “use exactly `vi-VN`.” They may provide an ordered preference list, conceptually like:

```text
preference 1 → vi-VN
preference 2 → vi
preference 3 → en-US
```

The application then needs **locale matching**: compare the client's preferences with the locales the product actually supports and choose the best match.

Java Core provides `Locale.LanguageRange` together with `Locale.lookup(...)` and `Locale.filter(...)` for language-range matching:

```java
List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(
        "vi-VN,vi;q=0.9,en-US;q=0.8"
);

List<Locale> supported = List.of(
        Locale.forLanguageTag("vi-VN"),
        Locale.US
);

Locale matched = Locale.lookup(ranges, supported);
```

Keep these concepts separate:

```text
language tag
→ represents one locale/preference

language priority list
→ several preferences with priorities

locale matching
→ chooses the best supported locale
```

A web framework may perform this step for the application, but understanding it explains why the active locale is often **selected from preferences**, not simply copied from one user-provided tag.

## <a id="language-script-region">Language, Script, and Region</a>

These components answer different questions:

| Component | Question | Examples |
| --- | --- | --- |
| language | which language? | `vi`, `en`, `zh` |
| script | which writing system? | `Latn`, `Cyrl`, `Hans`, `Hant` |
| region | which regional variant/conventions? | `VN`, `US`, `GB`, `TW` |

Beyond the three components beginners encounter most often, BCP 47 can also contain:

- **variants** for more specialized distinctions;
- **extensions** for structured additional information;
- **private-use** subtags beginning with `x-`.

Beginners do not need to memorize the entire BCP 47 grammar. The important point is that a tag **has structure**, so standard APIs should parse/build it instead of application code manually splitting strings.

Script matters when one language is commonly written in multiple scripts:

```java
Locale simplified = Locale.forLanguageTag("zh-Hans-CN");
Locale traditional = Locale.forLanguageTag("zh-Hant-TW");
```

Do not infer script from region when the script is explicitly known.

Similarly, `en-US` and `en-GB` share language `en` but can differ in date, currency, and vocabulary conventions.

## <a id="canonicalization-boundary">Canonicalization and Validation Boundary</a>

Use locale APIs rather than manually splitting language tags because BCP 47 also supports variants, extensions, and private-use subtags.

`Locale.forLanguageTag(...)` is intended for tag conversion and is relatively tolerant. If a public input contract requires strict rejection of malformed tags, `Locale.Builder` gives a clearer validation boundary:

```java
Locale locale = new Locale.Builder()
        .setLanguageTag(input)
        .build();
```

Ill-formed input can produce `IllformedLocaleException`.

This supports two different policies:

```text
trusted input / best-effort conversion
→ Locale.forLanguageTag(...)

strict public validation
→ Locale.Builder.setLanguageTag(...)
→ convert IllformedLocaleException into the application's validation error
```

### Syntactic validity is not application support

`fr-FR` can be a valid tag even if an application only ships resources for `vi-VN` and `en-US`.

Keep these questions separate:

```text
syntax validity
→ is this a structurally valid language tag?

application support
→ does this product have resources and policy for that locale?
```

The next chapter uses the selected `Locale` to solve a central localization problem: **loading localized messages without hard-coded language branches**.
