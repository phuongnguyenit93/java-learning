# Locale-Aware Text Segmentation

After learning how to translate, format, and sort text, another foundational question remains: **where should a piece of text be split?**

With simple English examples, it is easy to assume:

```text
character → one char
word      → split on spaces
sentence  → split on periods
line      → wrap at the nearest space
```

Those assumptions do not hold for all Unicode text or all languages. Java provides `BreakIterator` to analyze boundaries in natural-language text.

## <a id="breakiterator-model">What Is BreakIterator and Why Does It Exist?</a>

`BreakIterator` is an API for finding **valid boundary positions** in text.

It supports four major boundary types:

```text
character boundary
→ boundary of a user-perceived character unit

word boundary
→ natural-language word/token boundary

sentence boundary
→ sentence boundary

line boundary
→ legal/appropriate line-break opportunity
```

It exists because **text boundaries cannot always be inferred correctly from one fixed ASCII character**.

For example:

```text
"hello world"
```

has an obvious space, but not every writing system uses spaces like English. A period also does not always mean “end of sentence.”

The main factories are:

```java
BreakIterator.getCharacterInstance(locale);
BreakIterator.getWordInstance(locale);
BreakIterator.getSentenceInstance(locale);
BreakIterator.getLineInstance(locale);
```

After `setText(...)`, code moves through boundaries using methods such as `first()`, `next()`, `previous()`, `following(...)`, and `preceding(...)`.

## <a id="character-boundary">Character Boundaries and Grapheme Clusters</a>

The `string` module establishes an important Unicode distinction:

```text
char
≠ Unicode code point
≠ always one user-perceived character
```

Localization adds the next step: when code needs to move or cut text by **user-perceived character units**, it should not assume that each UTF-16 `char` is a complete character.

```java
BreakIterator iterator = BreakIterator.getCharacterInstance(locale);
iterator.setText(text);

for (int end = iterator.first(), start = end;
     (end = iterator.next()) != BreakIterator.DONE;
     start = end) {
    String unit = text.substring(start, end);
}
```

`getCharacterInstance(...)` helps locate boundaries corresponding to the character units a user perceives, rather than blindly slicing UTF-16 indexes.

This concept is closely related to Unicode **grapheme clusters**. Code-point, grapheme, and normalization theory remains owned by the `string` module; this chapter owns the localization-side use of **boundary analysis**.

Typical use cases include:

```text
cursor movement
text selection
safe truncation
UI character stepping
```

## <a id="word-sentence-boundary">Word and Sentence Boundaries</a>

`BreakIterator.getWordInstance(locale)` finds text segments according to locale-sensitive boundary rules:

```java
BreakIterator words = BreakIterator.getWordInstance(locale);
words.setText(text);
```

Do not interpret this as “return a clean list of words with punctuation removed.” `BreakIterator` primarily returns **boundary positions**; application code still decides which segments count as meaningful words for its use case.

Similarly:

```java
BreakIterator sentences = BreakIterator.getSentenceInstance(locale);
sentences.setText(text);
```

finds sentence boundaries without hard-coding literal-period splitting such as `split("\\.")`.

Why are `split(" ")` and even literal-period splitting with `split("\\.")` weak models?

```text
spaces are not a universal word delimiter
punctuation can occur inside abbreviations and numbers
sentence-ending punctuation differs across languages
Unicode text contains many whitespace/punctuation forms
```

`BreakIterator` moves language/Unicode boundary knowledge into a standard API instead of forcing application code to guess.

## <a id="line-boundary">Line Boundaries Are Not Just Newlines</a>

`BreakIterator.getLineInstance(locale)` finds **line-break opportunities** — places a renderer may consider when wrapping text.

That differs from:

```text
\n
→ a newline already present in the data
```

A line-break opportunity asks:

```text
If a UI must wrap a long line,
where is a valid boundary to consider breaking it?
```

This matters for UI/layout engines, document rendering, and text editors. A typical backend rarely performs its own text layout, but developers should still know that “wrap every N characters” can split natural-language text incorrectly.

## <a id="breakiterator-boundary">BreakIterator Role and Boundary</a>

`BreakIterator` solves **boundary analysis**; it is not a complete natural-language-processing system.

```text
BreakIterator does
→ character/word/sentence/line boundary analysis

BreakIterator does not automatically do
→ language translation
→ semantic understanding
→ stemming / lemmatization
→ full NLP tokenization for every domain
→ screen rendering
```

Locale selects the relevant boundary rules:

```java
BreakIterator iterator = BreakIterator.getWordInstance(
        Locale.forLanguageTag("vi-VN")
);
```

Keep ownership clear:

```text
String/Unicode module
→ how text is represented

Localization + BreakIterator
→ where natural-language boundaries occur

UI / NLP framework
→ uses those boundaries for selection, layout, or deeper analysis
```

The next chapter addresses another natural-language-text issue: **stored logical order and displayed visual order can differ when LTR and RTL content are mixed**.
