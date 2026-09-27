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

### A text block is source syntax, not a new runtime type

Two source forms can produce the same String value:

```java
String a = "hello\nworld\n";

String b = """
        hello
        world
        """;

a.equals(b); // true
```

All normal String rules for immutability, equality, encoding, and Unicode still apply.

### Opening delimiter

After the opening `"""`, Java text-block syntax requires a line terminator. Content begins on following lines, giving multiline source a clear structure.

## <a id="incidental-whitespace">Incidental Indentation</a>

The compiler removes a defined amount of indentation that belongs to source formatting, allowing a text block to sit naturally inside indented Java code.

Mental model:

```text
source indentation for readable Java code
        ↓ compiler determines incidental indentation
strip incidental part
        ↓
String content
```

The closing delimiter position can influence what indentation is considered incidental, so place content and delimiter consistently.

Whitespace inside the resulting text still matters. When exact output matters, inspect/test the actual String rather than relying only on visual indentation.

Trailing whitespace is also treated specially so invisible editor formatting does not silently become data. If trailing space is intentional, use an explicit mechanism such as `\s`.

### Trailing newline and the closing delimiter

These shapes can differ in whether the result ends with a newline:

```java
String withNewline = """
        hello
        """;

String withoutNewline = """
        hello""";
```

When exact output matters, assert `length()` or inspect an escaped representation rather than trusting visual source layout.

### String.indent and stripIndent

Multiline text does not only come from text-block literals. `String` also provides runtime indentation operations:

```java
String text = "alpha\nbeta\n";

String indented = text.indent(4);
String stripped = indented.stripIndent();
```

Mental model:

```text
text-block incidental indentation
→ compiler processing while creating the literal from source

String.indent / stripIndent
→ runtime String operations
```

The two ideas are related by purpose, but they are not the same mechanism.

## <a id="escape-processing">Escapes and Line Terminators</a>

Text blocks still process Java escape sequences and have defined line-terminator/closing-delimiter behavior.

They are not raw strings. If backslash or newline behavior must be exact, reason about the final Java String value.

### `\s` preserves an intentional space

```java
String value = """
        red  \s
        green\s
        """;
```

`\s` becomes a space after incidental-whitespace processing, which makes intentional trailing space visible in source.

### Line continuation

A backslash at the end of a physical line can suppress that line terminator:

```java
String sentence = """
        hello \
        world
        """;
```

This is source-level escape behavior, not String mutation.

### Processing order matters

A useful mental model is:

```text
normalize line terminators
→ strip incidental whitespace
→ process escape sequences
→ String value
```

That order explains why `\s` can preserve a space even though ordinary source whitespace may be stripped.

### translateEscapes

`translateEscapes()` is useful when a **runtime String** contains escape notation and the application intentionally wants to interpret it:

```java
String escaped = "line1\\nline2";
String translated = escaped.translateEscapes();

System.out.println(translated);
```

```text
runtime text "\\n"
→ translateEscapes()
→ newline character
```

This differs from Java source-literal escape processing: source escapes are handled by the compiler, while `translateEscapes()` is a runtime String operation.

Do not run `translateEscapes()` on arbitrary user input simply because it contains backslashes; interpreting escape notation must be part of the input contract.

## <a id="text-block-not-template">Text Blocks Are Not Templates</a>

Text blocks do not automatically interpolate variables:

```java
"""
Hello ${name}
"""
```

does not substitute `name` by itself.

Use formatting, concatenation, or an appropriate template mechanism when dynamic values must be inserted.

```java
String template = """
        Hello %s
        """;

String message = template.formatted(name);
```

A text block only improves source representation; it does not make `%s`, `${name}`, or arbitrary markers interpolate themselves without another API/mechanism.

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
