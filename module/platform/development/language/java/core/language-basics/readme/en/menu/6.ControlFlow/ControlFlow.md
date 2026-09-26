# Control Flow

Once a program has values and expressions, it needs to decide **which statements execute, how often, and when execution leaves a branch or loop**.

## <a id="branching-model">if and switch Branching</a>

`if/else` is natural for arbitrary boolean conditions. `switch` is useful when one selector is compared against a well-defined set of cases.

Modern Java supports both switch statements and switch expressions.

Choose the structure that makes the decision model easiest to understand, not merely the one with fewer characters.

## <a id="loop-control">Loops and break/continue</a>

Java's common loops are `for`, `while`, and `do-while`.

`break` exits the current loop; `continue` skips the rest of the current iteration.

Labeled `break`/`continue` can express some nested-loop flows, but deeply nested control flow is often a signal that extraction into smaller methods would be clearer.

## <a id="switch-expression">Switch Expressions and yield</a>

A switch expression produces a value:

```java
String label = switch (status) {
    case NEW -> "new";
    case DONE -> "done";
};
```

For a multi-statement case block, `yield` provides the switch-expression result.

Arrow-style cases also avoid accidental fall-through from classic colon-style switch statements.

## <a id="control-flow-pitfalls">Control-flow Pitfalls</a>

Readability suffers with deeply nested branches, side effects inside long conditions, accidental switch fall-through, or many exits spread across several loop levels.

Guard clauses, extracted methods, data-driven design, or polymorphism may express the decision more clearly.

The next chapter packages reusable behavior into methods and examines how a Java method call is selected.
