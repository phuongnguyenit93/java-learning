# Control Flow

## <a id="branching-model">if/switch branching model</a>
`if` chooses a path from boolean conditions. `switch` selects among discrete alternatives and modern switch expressions can produce values. Prefer a structure that makes mutually exclusive business rules obvious instead of deeply nested conditionals.

## <a id="loop-control">for/while/do-while and break/continue</a>
`for` is useful when initialization/update belong to the loop; enhanced `for` iterates an `Iterable` or array; `while` checks before each iteration; `do-while` runs the body at least once. `break` exits and `continue` skips to the next iteration. Labeled forms exist but should be rare.

## <a id="switch-expression">switch expression and yield</a>
Switch expressions use `->` or `yield` to produce a value and avoid accidental fall-through. Exhaustiveness matters when the compiler knows all alternatives, such as enums.

```java
String label = switch (status) {
    case NEW -> "new";
    case DONE -> "done";
};
```

## <a id="control-flow-pitfalls">Control-flow readability and common pitfalls</a>
Common problems include accidental classic-switch fall-through, loop termination depending on hidden mutation, duplicated conditions, and deeply nested branches. Guard clauses and small methods often make invariants clearer. Correct control flow should also communicate why a path exists.
