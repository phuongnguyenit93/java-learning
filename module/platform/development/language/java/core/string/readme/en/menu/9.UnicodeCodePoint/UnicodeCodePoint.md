# Unicode and Code Points

A common Java text misconception is that **one `char` always equals one user-visible character**. Modern Unicode makes that false.

## <a id="utf16-char-model">Java char and UTF-16</a>

`char` is a 16-bit **UTF-16 code unit**.

Many Unicode characters in the Basic Multilingual Plane fit in one code unit, while supplementary characters require two.

Therefore `String.length()` returns a UTF-16 code-unit count, not necessarily a Unicode code-point count or a user-perceived character count.

## <a id="code-point">Unicode Code Point</a>

A **code point** is a Unicode scalar/code value such as `U+0041` or `U+1F600`.

Java commonly represents code points as `int` because the Unicode space is larger than one `char`.

APIs such as `codePointCount` and `codePoints()` let code operate at that level.

## <a id="surrogate-pairs">Surrogate Pairs</a>

UTF-16 represents supplementary code points with a high-surrogate + low-surrogate pair.

One emoji can therefore produce:

```text
String.length() == 2
codePointCount(...) == 1
```

Iterating naïvely by `char` can split a valid code point in half.

## <a id="unicode-iteration">Code-point Iteration</a>

When the logic truly works with Unicode code points, use code-point-aware APIs:

```java
text.codePoints().forEach(cp -> ...);
```

or use `codePointAt` with `Character.charCount(cp)` when manually advancing an index.

Choose the representation level that matches the question; not every String loop needs code-point iteration.

## <a id="code-unit-code-point-grapheme">Code Unit vs Code Point vs Grapheme</a>

Keep three levels separate:

```text
UTF-16 code unit
→ storage unit behind basic char/String APIs

Unicode code point
→ Unicode coded value

grapheme cluster
→ what users often perceive as one displayed character
```

A grapheme cluster may contain several code points, such as a base letter plus combining mark or a multi-code-point emoji sequence.

Even a code-point count is therefore not always the same as “characters visible to the user”.

## <a id="unicode-normalization">Unicode Normalization</a>

Visually equivalent text can use different code-point sequences. A precomposed accented character and a base character plus combining mark are a common example.

`java.text.Normalizer` supports normalization forms such as NFC and NFD.

Normalization is a policy decision for comparison/search/storage, not something every String operation should perform automatically.

## <a id="canonical-equivalence">Canonical Equivalence</a>

Two canonically equivalent Strings can still satisfy:

```java
a.equals(b) == false
```

when their code-point sequences differ.

Normalizing both to the same form may make equality align with the use case.

`String.equals` compares String sequences; it does not automatically apply Unicode normalization or linguistic equivalence.

The next chapter moves from representation to pattern matching with regular expressions.
