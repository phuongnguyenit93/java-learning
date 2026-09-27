# Resource Fallback

A localization system rarely needs a completely independent resource file for every possible language-script-region combination. `ResourceBundle` therefore supports fallback: when an exact resource is unavailable, lookup can continue through less-specific candidates.

Fallback is useful, but “the application still displayed something” does not necessarily mean localization is complete or correct.

In plain language, **fallback is the strategy of looking for a less-specific resource when the most-specific resource is unavailable**.

It exists because, without fallback, an application would need a complete independent file for every precise locale or would fail whenever a small locale-specific variant was missing. Fallback enables reuse, but developers must understand where the final value actually came from.

The main pieces in the fallback process are:

```text
requested Locale
→ locale requested by the user/context

candidate locales
→ progressively less-specific lookup candidates

default Locale fallback
→ additional fallback path in the default ResourceBundle policy

base bundle
→ resource bundle with no locale suffix

missing-resource behavior
→ what happens when no acceptable bundle/key can be found
```

## <a id="bundle-candidate-chain">ResourceBundle Candidate Chain</a>

Suppose the base name is `Messages` and the requested locale is `en-US`.

Available bundles might include:

```text
Messages_en_US.properties
Messages_en.properties
Messages.properties
```

A beginner mental model is:

```text
start with the most specific requested locale
        ↓
try a more general candidate when necessary
        ↓
eventually reach the base bundle when applicable
```

Candidate locales can be inspected through `ResourceBundle.Control`:

```java
ResourceBundle.Control control = ResourceBundle.Control.getControl(
        ResourceBundle.Control.FORMAT_DEFAULT
);

List<Locale> candidates = control.getCandidateLocales(
        "Messages",
        Locale.forLanguageTag("en-US")
);
```

Locales containing script or variant subtags can have a richer chain than the simple `en-US` example. Let the JDK's resource-bundle algorithm own this behavior instead of implementing filename fallback manually.

## <a id="default-locale-fallback">Default Locale Fallback</a>

The default `ResourceBundle` lookup process can also consult the JVM's **default locale** when an appropriate bundle for the requested target locale cannot be found.

That makes the default locale a hidden dependency if the application has not defined a clear fallback policy.

For example:

```text
request asks for fr-FR
        ↓
French bundle is unavailable
        ↓
JVM default is en-US
        ↓
lookup may reach English resources through fallback policy
```

The application may avoid an exception yet still show the user an unexpected language.

When product requirements need strict behavior, fallback can be controlled explicitly:

```java
ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(
        ResourceBundle.Control.FORMAT_DEFAULT
);

ResourceBundle bundle = ResourceBundle.getBundle(
        "Messages",
        locale,
        control
);
```

Do not customize fallback merely because the API permits it. First define the product rule: should an unsupported locale fall back to a language-only bundle, a fixed product default, the base bundle, or an error?

## <a id="base-bundle">Role of the Base Bundle</a>

The base bundle has no locale suffix:

```text
Messages.properties
```

It can serve as the final general resource set in the bundle hierarchy.

Two common strategies are:

```text
Strategy A
base bundle = the product's practical default language, such as English

Strategy B
base bundle = a minimal safe fallback set
localized bundles contain full language-specific content
```

Neither strategy works well if the team has not agreed on it. For example, an English base bundle can quietly leak English into another locale when a translation key is missing.

Parent/fallback lookup also means a locale-specific bundle does not technically need to duplicate every key that exists in a parent. A product may still require 100% per-locale key completeness and enforce that with tests.

## <a id="missing-resource">MissingResourceException and Missing Keys</a>

Distinguish two kinds of failure:

```text
no acceptable bundle can be resolved
→ ResourceBundle.getBundle(...) can throw MissingResourceException

a bundle resolves but a requested key is absent from its lookup chain
→ getString(key) can throw MissingResourceException
```

Code can handle the exception according to product policy:

```java
try {
    String message = bundle.getString("order.created");
} catch (MissingResourceException ex) {
    // log, translate, or fail according to an explicit application policy
}
```

However, production UI should not be the first place missing translations are discovered. Build/test validation can compare keys across resource sets:

```text
base keys
        ↓ compare
locale-specific keys
        ↓
report missing or unexpected entries
```

Silently catching every missing resource and showing the key itself may keep the page alive, but it can also hide deployment/content defects indefinitely. If that fallback is used, it should be observable through logging or monitoring.

Fallback is a reuse and resilience mechanism, not a substitute for translation quality.

The final chapter collects common production mistakes where locale-sensitive behavior leaks into identifiers, serialization, or global environment assumptions.
