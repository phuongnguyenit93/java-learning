# Text Blocks

Text blocks make multiline String literals easier to read in source code. They change **source representation**, not the runtime String type.

## <a id="text-block-syntax">Text Block Syntax</a>

```java
String json = """
    {
      "name": "Java"
    }
    """;
```

The result is still an ordinary `java.lang.String`.

Text blocks are useful for JSON, SQL, HTML, and other multiline source literals because they reduce escape and concatenation noise.

## <a id="incidental-whitespace">Incidental Indentation</a>

The compiler removes a defined amount of indentation that belongs to source formatting, allowing a text block to sit naturally inside indented Java code.

Whitespace inside the resulting text still matters. When exact output matters, inspect/test the actual String rather than relying only on visual indentation.

## <a id="escape-processing">Escapes and Line Terminators</a>

Text blocks still process Java escape sequences and have defined line-terminator/closing-delimiter behavior.

They are not raw strings. If backslash or newline behavior must be exact, reason about the final Java String value.

## <a id="text-block-not-template">Text Blocks Are Not Templates</a>

Text blocks do not automatically interpolate variables:

```java
"""
Hello ${name}
"""
```

does not substitute `name` by itself.

Use formatting, concatenation, or an appropriate template mechanism when dynamic values must be inserted.

The module's final mental model is:

```text
String is immutable
→ safe sharing/pooling becomes possible
→ equality is content-based, not pool identity
→ builders provide mutable construction
→ text/bytes require an explicit Charset
→ char/code point/grapheme are different representation levels
→ regex describes text patterns
→ text blocks improve source syntax only
```
