# String Equality

String pooling can make `==` appear to compare content in simple demos. That is accidental identity sharing, not the String equality contract.

## <a id="string-equals">Content Equality with equals</a>

`String.equals` compares String content:

```java
String a = new String("java");
String b = new String("java");

a.equals(b) // true
```

When the question is “do these text values contain the same sequence?”, `equals` is the normal operation.

### equals is case-sensitive

```java
"Java".equals("java"); // false
```

`equals` checks String value equality; it does not perform Unicode normalization, locale-sensitive collation, or full linguistic equivalence.

### Null-safe comparison

Calling a method on `null` fails:

```java
String actual = null;
// actual.equals("java"); // NullPointerException
```

If the expected value is known to be non-null:

```java
"java".equals(actual); // false
```

If both values may be null:

```java
Objects.equals(left, right);
```

`Objects.equals` handles null first and delegates to `equals` for non-null values.

### Ordering is not equality

`compareTo` answers a lexicographic-order question:

```java
"abc".compareTo("abc"); // 0
"abc".compareTo("abd"); // < 0
"abd".compareTo("abc"); // > 0
```

Do not assume the result is always `-1`, `0`, or `1`; the sign is the relevant contract.

Natural String ordering is useful for technical ordering. Human-language ordering may require `Collator` from the localization boundary.

### contentEquals

When comparing with another `CharSequence`:

```java
String text = "java";
StringBuilder builder = new StringBuilder("java");

text.contentEquals(builder); // true
```

There is no need to convert every character sequence into String merely to compare content.

## <a id="string-reference-equality">== Checks Identity</a>

For references, `==` only asks whether both variables identify the same object.

Pooling may make some literals share identity, but correct text comparison should not depend on that optimization.

Mental model:

```text
a ─────► String object #1: "java"

b ─────► String object #2: "java"

a == b
→ are object #1 and object #2 the same object?

a.equals(b)
→ do they represent the same text sequence?
```

Use `==` with String only when identity is genuinely the thing being observed, such as a pool/intern experiment.

## <a id="case-insensitive-boundary">Case-insensitive Comparison</a>

`equalsIgnoreCase` is useful for some simple comparisons, but case rules can be language- and locale-sensitive.

Importantly, `equalsIgnoreCase` itself **does not use a Locale**. It applies String's case-insensitive comparison rules, so it is not locale-aware collation and can be unsatisfactory for some languages.

```text
protocol/technical identifier
→ usually needs stable locale-neutral rules

human-language text
→ may need locale-aware comparison/collation
```

Do not assume lowercasing with the platform default locale is a universal comparison strategy. Locale-specific text behavior belongs in the localization module.

A common pitfall is:

```java
a.toLowerCase().equals(b.toLowerCase())
```

The no-argument conversion uses the process default Locale and creates intermediate Strings. If the domain needs locale-sensitive comparison, Locale must be an explicit part of the contract.

For technical identifiers or protocol tokens, follow the protocol/domain rule rather than treating default-locale lowercasing as universal normalization.

Next we build a mental model for everyday String operations: inspection, search, extraction, transformation, split/join, and formatting.
