# Regular Expressions

## <a id="pattern-matcher">Pattern/Matcher model</a>
`Pattern` is the compiled regular expression; `Matcher` applies it to an input and maintains match state. Compile reusable patterns once when appropriate. `matches()` requires the entire region to match; `find()` searches for the next matching subsequence.

## <a id="regex-groups">Groups and captures</a>
Parentheses define capturing groups unless made non-capturing. Groups can be referenced by number or name and expose substrings from a successful match. Group numbering follows opening parentheses, so adding captures can change numeric indices; named groups can improve maintainability.

## <a id="regex-quantifiers">Greedy/reluctant quantifiers</a>
Greedy quantifiers consume as much as possible then backtrack; reluctant quantifiers consume as little as possible while allowing the rest to match; possessive quantifiers do not give consumed input back. Their differences affect both result and performance.

## <a id="regex-performance">Backtracking and performance pitfalls</a>
Nested ambiguous quantifiers can cause catastrophic backtracking on adversarial input. Regex is powerful for lexical patterns but is not always the right parser for nested/recursive formats. Bound input, prefer simpler deterministic patterns, and benchmark or redesign expressions used on untrusted large text.
