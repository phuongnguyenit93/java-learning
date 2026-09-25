# Unicode, char and Code Points

## <a id="utf16-char-model">Java char and UTF-16</a>
A Java `char` is a 16-bit UTF-16 code unit, not a guaranteed complete Unicode character. Basic Multilingual Plane code points often fit in one char; supplementary code points require a surrogate pair. String `length()` counts UTF-16 code units.

## <a id="code-point">Unicode code point</a>
A code point is a Unicode scalar/value identifier such as U+0041 or U+1F600. Java provides `codePointAt`, `codePointCount`, `offsetByCodePoints`, and `String.codePoints()` for code-point-aware processing.

## <a id="surrogate-pairs">Surrogate pairs</a>
Supplementary code points are encoded in UTF-16 using a high-surrogate plus low-surrogate pair. Indexing a String by `charAt` can split the pair, so algorithms that iterate user text character-by-character must decide whether code units or code points are the intended unit.

## <a id="unicode-iteration">Correct code-point iteration</a>
Use `codePoints()` or advance by `Character.charCount(codePoint)`/`offsetByCodePoints` instead of blindly incrementing a char index when supplementary characters matter. Code-point correctness still does not equal user-perceived-character correctness.

## <a id="code-unit-code-point-grapheme">UTF-16 code unit vs code point vs grapheme cluster</a>
A code unit is storage; a code point is a Unicode abstract character value; a grapheme cluster approximates one user-perceived character and may contain multiple code points, such as base letter + combining mark or emoji sequences. Cursor movement, truncation, and UI length may need grapheme-aware logic rather than `length()` or code-point count alone.

## <a id="unicode-normalization">Unicode normalization forms and Normalizer</a>
Unicode permits different code-point sequences to represent canonically equivalent text. `java.text.Normalizer` supports forms such as NFC/NFD (canonical composition/decomposition) and NFKC/NFKD (compatibility normalization). Choose normalization based on domain; compatibility normalization can intentionally change distinctions.

## <a id="canonical-equivalence">Canonical equivalence and String equality</a>
Precomposed `é` (U+00E9) and `e` + combining acute (U+0065 U+0301) can look the same but are different UTF-16/code-point sequences, so ordinary `String.equals` is false before normalization. Normalize at a defined boundary when canonical equivalence is part of identifiers/search/data matching.
