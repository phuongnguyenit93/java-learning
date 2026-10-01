# Bidirectional Text and LTR/RTL

Most introductory programming examples use English, so developers become accustomed to text flowing **left-to-right (LTR)**. Languages such as Arabic and Hebrew are primarily written **right-to-left (RTL)**.

More importantly, one line can mix both directions:

```text
RTL sentence + 12345 + EnglishProductCode
```

In that case, the order stored in the string and the order seen on screen cannot be understood by simply “reading left to right.”

## <a id="bidi-model">What Is Bidi? Logical Order vs Visual Order</a>

**Bidirectional text (bidi text)** is text that contains, or can contain, runs written in different directions.

Two concepts are fundamental:

```text
logical order
→ character order stored in the String / logical content order

visual order
→ the positions in which glyphs are presented after bidi rules are applied
```

The distinction matters because Unicode text should generally remain in **logical order** for consistent data processing, while a renderer applies the Unicode Bidirectional Algorithm to determine visual presentation.

Manually reversing a string to “support Arabic” is therefore dangerous, especially when numbers, punctuation, or embedded English appear inside Arabic/Hebrew content.

## <a id="ltr-rtl-runs">LTR, RTL, and Directional Runs</a>

A bidi paragraph can be analyzed into **directional runs**: consecutive ranges that share an embedding level/direction in the analysis result.

Mental model:

```text
paragraph
  ├─ run A → RTL
  ├─ run B → LTR (for example, a number or code)
  └─ run C → RTL
```

Beginner concepts to know:

```text
base direction
→ paragraph's overall base direction

LTR run
→ range handled left-to-right

RTL run
→ range handled right-to-left

embedding level
→ level used by the bidi algorithm to order directional runs
```

Developers do not need to implement the Unicode Bidirectional Algorithm. The key insight is that **mixed-direction text has structure**; it is not adequately modeled by one boolean such as `isRtl` for the whole string.

## <a id="java-bidi">java.text.Bidi in Java</a>

Java provides `java.text.Bidi` for bidirectional-text analysis.

```java
Bidi bidi = new Bidi(
        text,
        Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
);

boolean baseLtr = bidi.baseIsLeftToRight();
int runCount = bidi.getRunCount();
```

Individual runs can be inspected:

```java
for (int i = 0; i < bidi.getRunCount(); i++) {
    int start = bidi.getRunStart(i);
    int limit = bidi.getRunLimit(i);
    int level = bidi.getRunLevel(i);
}
```

The API can answer questions such as:

```text
what is the paragraph base direction?
does this text range need bidi processing?
how many directional runs exist?
where does each run start/end?
what is each run's embedding level?
```

`Bidi.requiresBidi(...)` can help determine whether a character range requires bidi processing.

## <a id="bidi-boundary">Bidi Analysis vs Rendering</a>

`Bidi` is **not a text renderer**.

It analyzes direction and run structure so a renderer or UI toolkit can perform ordering/layout correctly. Font selection, glyph shaping, measuring, pixel layout, and drawing belong to the rendering/UI stack.

Keep the boundary clear:

```text
String
→ stores logical text

java.text.Bidi
→ analyzes direction / runs

UI renderer / layout engine
→ produces actual visual output
```

Modern Java UI/framework stacks often handle bidi automatically, and backend code rarely needs to instantiate `Bidi` directly. Internationalization-aware developers still need the model to avoid anti-patterns such as:

```text
manually reversing Arabic/Hebrew strings
assuming all visible text is left-to-right
placing punctuation by string hacks based on visual position
treating a mixed-direction string as one uniform RTL block
```

One final rule:

```text
logical content
→ keep correct Unicode data

direction analysis
→ let Unicode bidi rules / standard APIs handle it

visual layout
→ let the renderer own it
```

After the human-text milestone, the final milestone returns to **module-wide safety policy**: `ResourceBundle` fallback, default Locale behavior, machine-stable data, and common localization pitfalls.
