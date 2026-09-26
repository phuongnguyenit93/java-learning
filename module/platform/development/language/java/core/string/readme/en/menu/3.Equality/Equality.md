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

## <a id="string-reference-equality">== Checks Identity</a>

For references, `==` only asks whether both variables identify the same object.

Pooling may make some literals share identity, but correct text comparison should not depend on that optimization.

## <a id="case-insensitive-boundary">Case-insensitive Comparison</a>

`equalsIgnoreCase` is useful for some simple comparisons, but case rules can be language- and locale-sensitive.

```text
protocol/technical identifier
→ usually needs stable locale-neutral rules

human-language text
→ may need locale-aware comparison/collation
```

Do not assume lowercasing with the platform default locale is a universal comparison strategy. Locale-specific text behavior belongs in the localization module.

Next we examine what concatenation means when Strings themselves cannot mutate.
