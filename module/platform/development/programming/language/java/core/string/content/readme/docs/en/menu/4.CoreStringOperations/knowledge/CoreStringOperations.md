# Core String Operations

After learning that `String` is immutable and that text equality is value-based, the next step is learning how to **inspect, search, extract, transform, split, join, convert, and format text** with the core `String` APIs. The goal is not to memorize every method in `java.lang.String`, but to build a mental model for choosing the right family of operations.

```text
What do I need to do with the text?
        ↓
inspect
        ↓
search
        ↓
extract
        ↓
transform
        ↓
split / join
        ↓
convert / format
```

Most operations that produce different content return a **new String**. The original String does not change.

## <a id="string-char-sequence">String as a CharSequence</a>

`String` implements `CharSequence`, an abstraction for a sequence of `char` values that can be read by index:

```text
CharSequence
    ↑
    ├── String
    ├── StringBuilder
    └── StringBuffer
```

The important point is that **CharSequence does not imply immutability**. `String` is immutable, while `StringBuilder` and `StringBuffer` are mutable character sequences.

Many APIs accept `CharSequence` so callers are not forced to provide exactly a `String`:

```java
String text = "java-core";
StringBuilder token = new StringBuilder("java");

text.contains(token);       // true
text.contentEquals(token);  // false here because the content differs from "java-core"
```

A key pitfall is that `CharSequence` does not define one cross-implementation equality contract:

```java
String s = "java";
StringBuilder b = new StringBuilder("java");

s.equals(b);        // false
s.contentEquals(b); // true
```

When the contract is content comparison against a `CharSequence`, there is no need to convert every character sequence into a `String` first; `contentEquals(...)` expresses that intent directly.

So:

```text
String-to-String content equality
→ equals

String-to-CharSequence content comparison
→ contentEquals when that contract fits

CharSequence abstraction
→ does not itself guarantee cross-implementation equals
```

## <a id="string-inspection">Inspecting String Content</a>

Inspection operations answer questions such as how long the String is, whether it is empty, whether it contains only whitespace, or which code unit is stored at an index.

```java
String text = " Java ";

text.length();   // 6
text.isEmpty();  // false
text.isBlank();  // false
text.charAt(1);  // 'J'
```

These three states are different:

```text
""        → empty
"   "     → not empty, but blank
" Java "  → neither empty nor blank
```

`isBlank()` uses `Character.isWhitespace(...)`: an empty String or one containing only code points that API classifies as whitespace is blank.

`length()` and `charAt()` operate in terms of **UTF-16 code units**. A Java `char` is not guaranteed to equal one user-visible character; the Unicode chapter develops that boundary in detail.

A valid `charAt(index)` index satisfies:

```text
0 <= index < length()
```

```java
"Java".charAt(4); // StringIndexOutOfBoundsException
```

With a supplementary character:

```java
String emoji = "😀";

emoji.length();  // 2
emoji.charAt(0); // high surrogate, not the whole emoji
```

## <a id="string-search">Searching Inside a String</a>

For simple literal text searches, direct String APIs are often clearer than regex:

```java
String path = "/api/users/42";

path.startsWith("/api/"); // true
path.endsWith("42");      // true
path.contains("users");   // true
path.indexOf("users");    // 5
path.lastIndexOf('/');     // 10
```

`indexOf(...)` and `lastIndexOf(...)` return `-1` when no match exists. If the returned position will later become a substring boundary, validate it first.

```java
int slash = path.lastIndexOf('/');
if (slash >= 0) {
    String id = path.substring(slash + 1);
}
```

Regex belongs here only when the real problem is **pattern matching**, not merely because some text must be searched.

`contains` does not interpret regex:

```java
System.out.println("file-123.txt".contains("\\d+")); // false
```

```text
literal search
→ contains / indexOf / startsWith / endsWith

pattern search
→ regex
```

## <a id="string-extraction">Extracting with Indexes and substring</a>

`substring` returns a String representing a range of the original text:

```java
String value = "JAVA-21";

value.substring(0, 4); // "JAVA"
value.substring(5);    // "21"
```

The range convention is:

```text
beginIndex inclusive
endIndex   exclusive
```

So `substring(0, 4)` includes indexes `0,1,2,3`.

A negative index, an index beyond `length()`, or `beginIndex > endIndex` violates the boundary and results in `IndexOutOfBoundsException`.

String indexes are UTF-16 code-unit indexes. Do not assume that `substring(i, i + 1)` extracts one user-visible character for arbitrary Unicode text.

Half-open ranges also make the slice length simple:

```text
substring(begin, end)

slice length = end - begin
```

Invalid boundaries include:

```text
begin < 0
end > length()
begin > end
```

## <a id="string-transformation">Text Transformation</a>

APIs such as `replace`, `trim`, `strip`, `toUpperCase`, and `toLowerCase` describe a **new value**:

```java
String raw = "  java-core  ";

String cleaned = raw.strip();
String renamed = cleaned.replace("core", "string");

System.out.println(raw);     // "  java-core  "
System.out.println(renamed); // "java-string"
```

`trim()` and `strip()` are not simply two names for the same rule. `strip()` removes leading/trailing characters according to `Character.isWhitespace(...)`, while `trim()` follows Java's historical rule of removing leading/trailing characters whose code is at most `U+0020`. `strip()` is therefore usually the better fit for Java's Unicode-aware whitespace API, but `Character.isWhitespace` should not be treated as identical to every Unicode whitespace property.

Related operations include:

```java
text.stripLeading();
text.stripTrailing();
```

### replace is not replaceAll

```java
"a.b".replace(".", "-");    // "a-b"
"a.b".replaceAll(".", "-"); // "---"
```

`replace` takes literal characters/sequences. `replaceAll` takes a regex, where `.` uses regex dot semantics: by default it matches a character except a line terminator, and `DOTALL` can change that rule. The `"a.b"` example has no line terminator, so all three characters are replaced.

### Some modern text operations

You do not need to memorize every `String` method, but several newer operations express intent clearly:

```java
String lines = "alpha\nbeta\n";

lines.lines().forEach(System.out::println);

"ab".repeat(3); // "ababab"
```

`lines()` produces a stream for iterating lines according to that API's line-terminator contract, avoiding manual scanning/slicing when the task is simply line-by-line processing. `repeat(n)` repeats text `n` times and is useful for simple formatting or test data.

APIs such as `indent` and `stripIndent` connect more directly to multiline text and continue in the Text Blocks chapter.

Case conversion also depends on intent:

```text
human-language text
→ may require Locale-aware rules

technical token / protocol identifier
→ needs rules defined by that contract
```

Full locale and collation behavior belongs to the `localization` module. This chapter establishes the boundary so lower/upper-case conversion is not treated as a universal normalization strategy.

## <a id="string-split-join">Splitting and Joining Text</a>

`split` turns one String into multiple parts, while `String.join` combines parts using a delimiter:

```java
String csv = "red,green,blue";
String[] colors = csv.split(",");

String path = String.join("/", "api", "users", "42");
// "api/users/42"
```

An important rule is that `String.split(...)` accepts a **regular expression**, not always a literal delimiter.

```java
"a.b.c".split("\\.");
```

This example has both Java String-literal escaping and regex escaping. The Regex chapter explains those two layers in depth.

When a delimiter is intended to be literal but has regex meaning, quote it deliberately or choose an API whose contract matches the task instead of guessing the escaping rules.

### split(regex, limit) changes result preservation

`split(regex)` has the same behavior as `split(regex, 0)`: the regex is applied repeatedly and trailing empty strings are discarded:

```java
System.out.println("a,b,".split(",").length);    // 2
System.out.println("a,b,".split(",", 0).length); // 2
```

When `limit < 0`, matching is not positively bounded and trailing empty strings are preserved:

```java
System.out.println("a,b,".split(",", -1).length); // 3
```

When `limit > 0`, the result contains at most `limit` elements, so the regex is applied at most `limit - 1` times. The final element keeps the remaining input:

```java
Arrays.toString("a,b,c,d".split(",", 2));
// [a, b,c,d]

Arrays.toString("a,b,c,d".split(",", 3));
// [a, b, c,d]
```

Mental model:

```text
limit > 0
→ at most limit elements
→ final element keeps the remainder

limit == 0
→ no positive split bound
→ discard trailing empty strings

limit < 0
→ no positive split bound
→ preserve trailing empty strings
```

`limit` is a data-parsing contract, not merely a performance option. If trailing empty fields or the unsplit remainder have semantic meaning, choose the overload deliberately.

This also shows why real CSV may require a parser: quoting and escaping rules are more complex than a simple delimiter.

## <a id="string-conversion-formatting">Converting Values and Formatting Output</a>

`String.valueOf(...)` is a common entry point for representing primitive or object values as text:

```java
String count = String.valueOf(42);
String active = String.valueOf(true);
```

Null behavior is worth knowing:

```java
String.valueOf((Object) null); // "null"
```

while:

```java
Object value = null;
// value.toString(); // NullPointerException
```

For structured output, Java also provides formatting:

```java
String message = "User %s has %d points".formatted("Ada", 42);
```

Formatting does not change String immutability; it produces another String result.

If formatting depends on language, numbers, dates, or regional presentation rules, cross into the `localization` module rather than assuming one format is correct for every locale.

The mental model to keep from this chapter is:

```text
core String APIs
→ inspect text
→ search for content/positions
→ extract with explicit boundaries
→ create transformed values
→ split/join multiple pieces
→ convert/format output text

none of these mutate the original String object
```

The next chapter focuses on one especially important construction operation: **concatenation**, where immutability directly affects how text is built across multiple steps.
