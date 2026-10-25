# ResourceBundle

Once an application knows the user's `Locale`, it still needs a scalable way to answer: **which message or resource should be used for that locale?** Hard-coded language branches do not scale. `ResourceBundle` separates localizable resources from Java source and resolves them by locale.

## <a id="resourcebundle-model">ResourceBundle Lookup Model</a>

In plain language, `ResourceBundle` is a **key + Locale resource lookup mechanism**.

It does not translate English into Vietnamese automatically. Developers or translators **prepare localized resources in advance**, and `ResourceBundle` selects the appropriate one at runtime.

A bundle system usually has five important pieces:

```text
base name
→ name of the resource family, for example Messages

Locale
→ locale being served, for example vi-VN

bundle family
→ Messages.properties, Messages_vi.properties, Messages_vi_VN.properties...

resource key
→ stable identifier such as order.created

resource value
→ actual content such as "Đơn hàng đã được tạo"
```

The role of `ResourceBundle` is to connect these pieces and apply candidate/fallback rules to find the appropriate value.

Suppose the UI needs the semantic message `order.created`.

Without a bundle, code may become:

```java
String message;
if (locale.getLanguage().equals("vi")) {
    message = "Đơn hàng đã được tạo";
} else {
    message = "Order created";
}
```

`ResourceBundle` changes the model:

```text
code owns a stable key
        ↓
order.created
        ↓
ResourceBundle + Locale
        ↓
matching resource
        ↓
localized text
```

For example:

```text
Messages.properties
Messages_en.properties
Messages_en_US.properties
Messages_vi.properties
Messages_vi_VN.properties
```

```properties
# Messages_en.properties
order.created=Order created

# Messages_vi.properties
order.created=Đơn hàng đã được tạo
```

Lookup code stays language-neutral:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
ResourceBundle bundle = ResourceBundle.getBundle("Messages", locale);

String message = bundle.getString("order.created");
```

`Messages` is the **base name**, `order.created` is the stable **resource key**, and the locale participates in selecting the best bundle.

## <a id="bundle-naming">Bundle Naming and Candidate Locales</a>

Resource bundles follow a naming convention based on the base name and locale components.

For:

```java
ResourceBundle.getBundle("Messages", Locale.forLanguageTag("en-US"));
```

a beginner-friendly model for **bundle resolution** is:

```text
requested Locale en-US
        ↓
generate candidate locales
        ↓
en-US → en → Locale.ROOT
        ↓
locate a bundle for those candidates
```

More complex locales containing script or variant components can produce a richer candidate list. Do not recreate that algorithm with string concatenation.

Once a bundle is resolved, **key lookup is a separate mechanism**: if a key is absent from the concrete bundle, lookup may continue through its established parent chain. Do not conflate “which bundle was resolved” with “which parent ultimately supplied a key.”

You can inspect the candidate locales through `ResourceBundle.Control`:

```java
ResourceBundle.Control control = ResourceBundle.Control.getControl(
        ResourceBundle.Control.FORMAT_DEFAULT
);

List<Locale> candidates = control.getCandidateLocales(
        "Messages",
        Locale.forLanguageTag("en-US")
);
```

Fallback is a **lookup rule**. It does not mean `Locale.forLanguageTag("en-US")` equals `Locale.forLanguageTag("en")`. Default-locale fallback policy is intentionally deferred to the final milestone.

## <a id="properties-vs-class-bundle">Properties vs Class-Based Bundles</a>

Java supports two common resource-bundle forms.

### `.properties` bundles

```text
Messages_en.properties
Messages_vi.properties
```

They are usually a good fit for translated messages because they keep text outside compiled Java code and work well with translation tooling.

### Class-based bundles

A class can extend `ListResourceBundle`:

```java
public class Messages_en extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
        return new Object[][] {
                {"order.created", "Order created"}
        };
    }
}
```

Class bundles can provide objects rather than only text values, but they couple the resource to Java compilation and source structure.

For ordinary message translation, `.properties` is typically simpler. Modern Java `PropertyResourceBundle` behavior supports UTF-8 resource content, while retaining compatibility behavior for older encodings. A project should still standardize its source/resource encoding explicitly across editors and build tooling.

## <a id="bundle-cache">ResourceBundle Caching Boundary</a>

`ResourceBundle.getBundle(...)` caches loaded bundles so each lookup does not repeatedly reload resources.

That has two sides:

```text
performance
→ repeated requests can reuse bundle instances

runtime updates
→ changing a resource file does not imply an already-running JVM immediately sees it
```

The cache can be cleared when a design truly requires it:

```java
ResourceBundle.clearCache();
ResourceBundle.clearCache(classLoader);
```

`ResourceBundle.Control` can customize TTL, reload, and candidate behavior, but that is an advanced mechanism. Decide first whether the application actually needs live resource reloading or can treat bundles as deployment-time content.

The distinction between **missing bundles** and **missing keys**, together with `MissingResourceException` policy, is owned by the final fallback milestone so it can be learned with the complete `ResourceBundle` failure/fallback model.

The next chapter handles the next problem: localized messages often include dynamic values. Concatenating fragments assumes one sentence structure, while `MessageFormat` lets the translated pattern own argument placement.
