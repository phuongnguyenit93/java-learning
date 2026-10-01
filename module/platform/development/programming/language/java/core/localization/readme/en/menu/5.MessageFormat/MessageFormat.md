# MessageFormat

`ResourceBundle` selects the correct message pattern, but real messages often contain dynamic values such as an order id, customer name, count, date, or amount. Simple string concatenation makes the developer's word order part of the program. `MessageFormat` gives the localized pattern control over where arguments appear.

## <a id="messageformat-model">MessageFormat Placeholders</a>

`MessageFormat` is a **formatter for a parameterized message template**. It takes a pattern, argument values, and a `Locale`, then produces the final message.

Its main pieces are:

```text
pattern
→ "Order {0} was created for {1}."

placeholder / argument index
→ {0}, {1}, ...

argument value
→ 1001, "An", ...

optional format type/style
→ number, date, choice...

Locale
→ influences locale-sensitive sub-formats
```

Its role is **not translation**. `ResourceBundle` typically supplies the already-translated message pattern; `MessageFormat` inserts the dynamic values into that pattern correctly.

A basic pattern uses numeric argument indexes:

```text
Order {0} was created for {1}.
```

```java
MessageFormat format = new MessageFormat(
        "Order {0} was created for {1}.",
        Locale.US
);

String text = format.format(new Object[] {1001L, "An"});
```

Bundles can keep the same semantic key while changing sentence structure:

```properties
# English
order.created=Order {0} was created for {1}.

# Vietnamese
order.created=Đơn hàng {0} của {1} đã được tạo.
```

Application code supplies the arguments, while the translator owns the full sentence:

```java
String pattern = bundle.getString("order.created");
MessageFormat formatter = new MessageFormat(pattern, locale);
String message = formatter.format(new Object[] {order.id(), customerName});
```

This is safer than fragment concatenation because languages are free to place arguments differently.

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

## <a id="messageformat-types">Number, Date, and Choice Formatting</a>

`MessageFormat` can apply sub-formats:

```text
Total: {0,number}
Created: {1,date,medium}
```

```java
MessageFormat format = new MessageFormat(
        "Total: {0,number} - Created: {1,date,medium}",
        Locale.US
);
```

These historical `java.text` sub-formats integrate with number/date types understood by that API family. Applications whose domain uses `java.time` often format date-time values explicitly with `DateTimeFormatter` and then pass the localized result into a message, keeping the conversion boundary clear.

`choice` formatting exists for numeric ranges, but real-world plural rules vary significantly across languages. Do not assume a simple English `1 item / many items` rule is linguistically universal.

The Java Core lesson is broader: **parameterized messages must let the translated pattern own grammar and must use the intended locale for any nested formatting**.

## <a id="quote-escaping">Apostrophe Quoting and Escaping</a>

`MessageFormat` uses `'` as a quoting character in its pattern language. This is a frequent source of bugs because natural-language translations can contain apostrophes.

To produce a literal apostrophe inside the pattern, doubled apostrophes are commonly required:

```java
MessageFormat format = new MessageFormat(
        "User ''{0}'' has {1,number} orders",
        Locale.US
);
```

Treat translated values used as `MessageFormat` input as **patterns**, not as opaque strings:

```text
translated resource
        ↓
MessageFormat pattern syntax
        ↓
placeholders + apostrophe quoting
        ↓
must be validated/tested with real arguments
```

If a message has no parameters, it does not need to be forced through `MessageFormat` merely for consistency.

## <a id="messageformat-locale">Locale-Specific MessageFormat</a>

`MessageFormat` owns a locale because nested number/date formatting depends on locale data.

```java
MessageFormat vi = new MessageFormat(
        "Tổng: {0,number}",
        Locale.forLanguageTag("vi-VN")
);

MessageFormat us = new MessageFormat(
        "Total: {0,number}",
        Locale.US
);
```

The same numeric argument can therefore render with different separators.

A coherent pipeline is:

```text
request/user Locale
        ↓
ResourceBundle(locale)
        ↓
localized pattern
        ↓
MessageFormat(pattern, same locale)
        ↓
localized message
```

Avoid selecting a Vietnamese pattern and accidentally formatting nested arguments with an unrelated JVM default locale.

`MessageFormat` is mutable, and `java.text` formatter instances should not be assumed safe for unsynchronized shared mutation across threads. Request-local instances or deliberately managed synchronization/caching are clearer choices in server code.

The next chapter focuses on one common argument type in more detail: numeric values.
