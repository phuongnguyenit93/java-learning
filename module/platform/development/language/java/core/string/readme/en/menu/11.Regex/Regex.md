# Regular Expressions

A regular expression (**regex**) is a pattern language for text. It is useful for validation, search, extraction, and replacement when the problem is genuinely pattern-oriented.

## <a id="pattern-matcher">Pattern and Matcher</a>

Java separates the compiled pattern from matching state:

```java
Pattern pattern = Pattern.compile("(\\d+)-(\\w+)");
Matcher matcher = pattern.matcher(input);
```

`Pattern` represents the compiled regex; `Matcher` applies it to a particular input and maintains matching state.

Repeatedly compiling the same regex in a hot loop may be unnecessary; reuse `Pattern` when appropriate.

### Pattern and Matcher have different roles

```text
regex source
    ↓ compile
Pattern
    ↓ matcher(input)
Matcher
    ↓ execute/search + maintain match state
```

`Pattern` is immutable and shareable; `Matcher` carries mutable matching state and is normally created per input/use.

### matches, lookingAt, and find

They answer different questions:

```java
Pattern digits = Pattern.compile("\\d+");
String input = "123 abc 456";

digits.matcher(input).matches();   // does the entire input match?
digits.matcher(input).lookingAt(); // does a match begin at the start?

Matcher finder = digits.matcher(input);
while (finder.find()) {
    String match = finder.group();
}
```

```text
whole-input validation
→ matches

prefix pattern
→ lookingAt

search / repeated extraction
→ find
```

### Java escaping and regex escaping are two layers

To make the runtime regex `\d+`:

```java
Pattern.compile("\\d+");
```

```text
Java source "\\d+"
        ↓ Java string parsing
runtime String "\d+"
        ↓ regex parsing
digit pattern
```

This is why Java regexes often appear to contain “double backslashes”.

When runtime text must be matched literally rather than interpreted as regex syntax, `Pattern.quote(text)` is often the correct boundary.

### Basic building blocks

```text
[abc]     character class
[^abc]    negated class
^ / $     anchors
a|b       alternation
(...)     group
\d \w   predefined classes
```

Do not reach for regex when a direct literal String operation is clearer.

### Pattern flags change matching semantics

The same regex source can have different semantics when compiled with flags:

```java
Pattern multiline = Pattern.compile(
        "^error:",
        Pattern.MULTILINE
);

Pattern dotAll = Pattern.compile(
        "BEGIN.*END",
        Pattern.DOTALL
);
```

Important flags include:

```text
CASE_INSENSITIVE
→ case-insensitive matching

MULTILINE
→ ^ and $ can operate at line boundaries

DOTALL
→ . also matches line terminators

UNICODE_CASE
→ Unicode-aware case folding when combined with case-insensitive behavior

UNICODE_CHARACTER_CLASS
→ predefined/POSIX character classes use broader Unicode semantics
```

Do not enable flags “just in case”. A flag is part of the regex contract and can affect both correctness and performance.

Unicode regex semantics should also not be confused with locale-sensitive language rules: regex flags and `Locale/Collator` solve different abstraction layers.

## <a id="regex-groups">Groups and Captures</a>

Parentheses create capturing groups by default:

```regex
(\d+)-(\w+)
```

After a match, `group(1)` and `group(2)` expose the captured text.

Use non-capturing groups `(?:...)` when grouping is needed but capture is not. Named groups can make complex patterns easier to understand.

```java
Pattern p = Pattern.compile(
        "(?<user>[a-z]+)@(?<domain>[a-z.]+)"
);
Matcher m = p.matcher("ada@example.com");

if (m.matches()) {
    m.group("user");   // ada
    m.group("domain"); // example.com
}
```

### group(0) and group(n)

After a successful match:

```text
group(0)
→ the complete match

group(1..n)
→ the corresponding capturing group
```

Calling `group(...)` without a successful match state throws `IllegalStateException`.

### Replacement has syntax too

In `replaceAll` / `Matcher.replaceAll` replacement text, `$` and `\` have special meaning. When a runtime replacement must be literal, use `Matcher.quoteReplacement(...)` where appropriate.

## <a id="regex-quantifiers">Greedy vs Reluctant Quantifiers</a>

Quantifiers such as `*`, `+`, and `{m,n}` are typically greedy by default: they consume as much as possible and backtrack when necessary.

Reluctant forms such as `*?` and `+?` begin with less and expand when needed.

Java also supports **possessive quantifiers** such as `*+` and `++`, which do not backtrack the consumed portion. They can control backtracking when the semantics fit, but they are not a drop-in “faster greedy” replacement.

The difference matters when several match boundaries are possible. Always reason about a pattern together with representative input.

Example:

```text
input: <a><b>

<.*>
→ greedy, can consume <a><b>

<.*?>
→ reluctant, first match can be <a>
```

## <a id="regex-performance">Backtracking and Performance</a>

Some ambiguous/nested quantifier patterns can generate enormous backtracking on adversarial input.

Risky shape:

```regex
(a+)+$
```

A long near-match that fails at the end can force the engine to explore many partitions before concluding.

For untrusted input:

- keep patterns simple;
- bound input where appropriate;
- avoid known catastrophic-backtracking structures;
- test worst cases rather than only successful examples.

Also consider:

- reuse a compiled `Pattern` when the same regex runs repeatedly;
- use a parser/state machine when the grammar is beyond regex's useful scope;
- place input-size/time boundaries around untrusted large input at the application layer;
- review nested quantifiers and overlapping alternatives carefully.

Regex is powerful, but it is not the right parser for every complex grammar.

The final chapter changes source-code syntax for multiline text, not the runtime String type.
