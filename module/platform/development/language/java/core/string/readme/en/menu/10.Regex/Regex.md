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

## <a id="regex-groups">Groups and Captures</a>

Parentheses create capturing groups by default:

```regex
(\d+)-(\w+)
```

After a match, `group(1)` and `group(2)` expose the captured text.

Use non-capturing groups `(?:...)` when grouping is needed but capture is not. Named groups can make complex patterns easier to understand.

## <a id="regex-quantifiers">Greedy vs Reluctant Quantifiers</a>

Quantifiers such as `*`, `+`, and `{m,n}` are typically greedy by default: they consume as much as possible and backtrack when necessary.

Reluctant forms such as `*?` and `+?` begin with less and expand when needed.

The difference matters when several match boundaries are possible. Always reason about a pattern together with representative input.

## <a id="regex-performance">Backtracking and Performance</a>

Some ambiguous/nested quantifier patterns can generate enormous backtracking on adversarial input.

For untrusted input:

- keep patterns simple;
- bound input where appropriate;
- avoid known catastrophic-backtracking structures;
- test worst cases rather than only successful examples.

Regex is powerful, but it is not the right parser for every complex grammar.

The final chapter changes source-code syntax for multiline text, not the runtime String type.
