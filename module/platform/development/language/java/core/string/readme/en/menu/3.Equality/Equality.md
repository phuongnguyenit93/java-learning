# String Equality

## <a id="string-equals">String content equality</a>
`String.equals` compares the sequence of UTF-16 code units. Two distinct String objects with the same sequence are equal. Hash-based collections rely on the matching `hashCode` implementation.

## <a id="string-reference-equality">Why == is not content comparison</a>
`==` on String references asks whether both references identify the same object. Pooling can make some equal strings also identical, which is why incorrect `==` code may appear to work in tests and then fail for runtime-created strings.

## <a id="case-insensitive-boundary">Case-insensitive comparison and locale boundary</a>
Case-insensitive text comparison is not a universal substitute for domain-specific normalization. `equalsIgnoreCase` uses Unicode case concepts without a Locale parameter; locale-sensitive case conversion such as Turkish I belongs to localization-aware processing. Define whether identifiers, user text, or search keys require locale-neutral or locale-aware rules.
