# Unicode and Code Points

A common Java text misconception is that **one `char` always equals one user-visible character**. Modern Unicode makes that false.

## <a id="utf16-char-model">Java char and UTF-16</a>

`char` is a 16-bit **UTF-16 code unit**.

Many Unicode characters in the Basic Multilingual Plane fit in one code unit, while supplementary characters require two.

Therefore `String.length()` returns a UTF-16 code-unit count, not necessarily a Unicode code-point count or a user-perceived character count.

Example:

```java
String text = "A😀";

text.length(); // 3 code units: 'A' + surrogate pair
```

This is why index-based APIs such as `charAt` and `substring` operate in UTF-16 code-unit offsets.

## <a id="code-point">Unicode Code Point</a>

A **code point** is a Unicode code value such as `U+0041` or `U+1F600`.

Java commonly represents code points as `int` because the Unicode space is larger than one `char`.

Code points are commonly written as:

```text
U+0041  → A
U+00E9  → é
U+1F600 → 😀
```

APIs such as `codePointCount` and `codePoints()` let code operate at that level.

Another useful helper is:

```java
Character.toChars(0x1F600); // surrogate-pair char[] for 😀
```

An `int` is not automatically a valid Unicode code point; use `Character.isValidCodePoint` when the value is not trusted.

## <a id="surrogate-pairs">Surrogate Pairs</a>

UTF-16 represents supplementary code points with a high-surrogate + low-surrogate pair.

One emoji can therefore produce:

```text
String.length() == 2
codePointCount(...) == 1
```

Iterating naïvely by `char` can split a valid code point in half.

```java
String emoji = "😀";

char high = emoji.charAt(0);
char low  = emoji.charAt(1);

Character.isHighSurrogate(high); // true
Character.isLowSurrogate(low);   // true
```

Those two `char` values are not two user-visible characters.

## <a id="unicode-iteration">Code-point Iteration</a>

When the logic truly works with Unicode code points, use code-point-aware APIs:

```java
text.codePoints().forEach(cp -> ...);
```

or use `codePointAt` with `Character.charCount(cp)` when manually advancing an index.

For indexed iteration:

```java
for (int i = 0; i < text.length(); ) {
    int cp = text.codePointAt(i);
    // process cp
    i += Character.charCount(cp);
}
```

When moving by N code points from an existing offset, `offsetByCodePoints` is safer than manually incrementing by one code unit.

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

Example:

```text
"e" + COMBINING ACUTE ACCENT

code points = 2
user often perceives = one "é" grapheme
```

Emoji sequences can also combine multiple code points using variation selectors, skin-tone modifiers, or zero-width joiners.

Choose the abstraction that matches the problem:

```text
Java String storage/indexing
→ code unit may be correct

Unicode symbol processing
→ code point

cursor/delete/count by user-perceived character
→ grapheme boundary
```

### HOW — handling grapheme boundaries in Java

Core String APIs do not provide a “grapheme index” replacement for `charAt(i)`. When UI/editor logic genuinely needs to move or cut text by user-perceived characters, use an appropriate segmentation API.

Java provides `BreakIterator`:

```java
BreakIterator iterator =
        BreakIterator.getCharacterInstance(locale);

iterator.setText(text);
```

It provides locale-aware text-boundary iteration. Full grapheme segmentation is a broader Unicode/localization topic, so this chapter keeps the boundary explicit:

```text
code-unit indexing
→ core String APIs

code-point processing
→ codePoints / codePointAt

user-perceived character boundaries
→ segmentation API such as BreakIterator
→ deeper localization/Unicode policy when the domain requires it
```

### A String can contain an unpaired surrogate

A `String` is a sequence of UTF-16 code units; it does not automatically guarantee that every sequence is well-formed Unicode text.

```java
String malformed = "\uD83D"; // isolated high-surrogate code unit
```

So “I have a String” is not the same as “I have already validated a Unicode scalar sequence”. This matters especially when encoding bytes or accepting text from an untrusted boundary.

## <a id="unicode-normalization">Unicode Normalization</a>

Visually equivalent text can use different code-point sequences. A precomposed accented character and a base character plus combining mark are a common example.

`java.text.Normalizer` supports normalization forms such as NFC and NFD.

The main forms are:

```text
NFD
→ canonical decomposition

NFC
→ canonical decomposition + composition

NFKD / NFKC
→ compatibility normalization
```

NFKC/NFKD may remove compatibility distinctions beyond canonical normalization, so do not choose them merely because they sound “stronger”.

Normalization is a policy decision for comparison/search/storage, not something every String operation should perform automatically.

Do not normalize every input blindly. Identifiers, security-sensitive tokens, signature inputs, or external protocols may require the exact sequence.

## <a id="canonical-equivalence">Canonical Equivalence</a>

Two canonically equivalent Strings can still satisfy:

```java
a.equals(b) == false
```

when their code-point sequences differ.

Normalizing both to the same form may make equality align with the use case.

`String.equals` compares String sequences; it does not automatically apply Unicode normalization or linguistic equivalence.

Concrete example:

```java
String composed = "é";
String decomposed = "e\u0301";

composed.equals(decomposed); // false

String a = Normalizer.normalize(composed, Normalizer.Form.NFC);
String b = Normalizer.normalize(decomposed, Normalizer.Form.NFC);

a.equals(b); // true
```

Canonical equivalence is a Unicode concept; linguistic equality or search may require a higher locale/collation layer.

The next chapter moves from representation to pattern matching with regular expressions.
