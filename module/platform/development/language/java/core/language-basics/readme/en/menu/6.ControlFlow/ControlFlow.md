# Control Flow

Once a program has values and expressions, it needs to decide **which statements execute, how often, and when execution leaves a branch or loop**.

## <a id="branching-model">if and switch Branching</a>

`if/else` is natural for arbitrary boolean conditions. `switch` is useful when one selector is compared against a well-defined set of cases.

Modern Java supports both switch statements and switch expressions.

Choose the structure that makes the decision model easiest to understand, not merely the one with fewer characters.

Java conditions require actual booleans; there is no numeric/reference truthiness:

```java
int count = 1;
// if (count) { } // invalid
if (count > 0) { }
```

Classic colon-style switch statements can fall through unless execution leaves the case. Arrow-style cases avoid accidental fall-through when cases are independent.

## <a id="loop-control">Loops and break/continue</a>

Java's common loops are `for`, `while`, and `do-while`.

`break` exits the current loop; `continue` skips the rest of the current iteration.

Labeled `break`/`continue` can express some nested-loop flows, but deeply nested control flow is often a signal that extraction into smaller methods would be clearer.

Choose loops by intent: indexed `for` when position matters, enhanced-for when only elements matter, `while` for condition-driven repetition, and `do-while` when the body must execute at least once.

```java
int[] values = {1, 2, 3};
for (int value : values) {
    value = 0; // changes only the local loop variable
}
```

Use indexes or another API when the array slot itself must change.

### HOW - What order does a loop actually execute?

A classic `for` loop follows this cycle:

```text
initialization       // once
      ↓
condition
 ├─ false → exit
 └─ true
      ↓
body
      ↓
update
      ↓
back to condition
```

Therefore `continue` in a classic `for` transfers control to the **update expression** before the next condition check:

```java
for (int i = 0; i < 3; i++) {
    if (i == 1) {
        continue; // i++ still runs before the next condition check
    }
    System.out.println(i);
}
```

`while` checks its condition before the body and may run zero times. `do-while` executes the body first, so it runs at least once.

This execution model explains `continue`, termination, and side effects better than memorizing syntax alone.

## <a id="switch-expression">Switch Expressions and yield</a>

A switch expression produces a value:

```java
String label = switch (status) {
    case NEW -> "new";
    case DONE -> "done";
    default -> "other";
};
```

For a multi-statement case block, `yield` provides the switch-expression result.

Arrow-style cases also avoid accidental fall-through from classic colon-style switch statements.

Switch expressions must produce a value on the required paths. `yield` returns a value from a switch-expression block; it does not return from the enclosing method.

### Java 21: switch can match type patterns

In Java 21, `switch` is not limited to comparing a selector with constants. A reference selector may be matched using type patterns:

```java
static String describe(Object value) {
    return switch (value) {
        case Integer i -> "integer: " + i;
        case String s -> "string: " + s;
        case null -> "null";
        default -> "other";
    };
}
```

Mental model:

```text
switch selector
→ constant/enum/String-style matching when appropriate
→ type-pattern matching for reference values
→ bind a pattern variable when a case matches
→ optionally handle null explicitly with case null
```

`case Integer i` both tests the runtime type and binds `i` with static type `Integer` inside that case. This is the same family of idea as `instanceof Integer i`, applied to a switch decision table.

If a reference selector may be `null`, treat that as part of the contract. `case null` handles it explicitly; without an applicable null case, switching on null can fail rather than simply falling through to `default`.

Do not mechanically replace every `if/else instanceof` chain with pattern switch. Use it when several branches classify the same selector and the switch form makes the decision model clearer.

## <a id="control-flow-pitfalls">Control-flow Pitfalls</a>

Readability suffers with deeply nested branches, side effects inside long conditions, accidental switch fall-through, or many exits spread across several loop levels.

Guard clauses, extracted methods, data-driven design, or polymorphism may express the decision more clearly.

Guard clauses can flatten the happy path:

```java
if (user == null) return;
if (!user.isActive()) return;
process(user);
```

### `return` is a control-flow exit

`return` does more than optionally provide a value: it ends execution of the current method immediately.

```java
if (!valid) {
    return;
}

process(); // runs only when valid
```

`break` exits an applicable loop/switch, `continue` ends the current loop iteration, while `return` exits the whole method. Distinguishing these exit levels makes nested control flow easier to reason about.

For condition-controlled loops, make the termination mechanism visible. Infinite loops may be intentional, but then the surrounding design should provide an explicit stop/interrupt mechanism where appropriate.

The next chapter packages reusable behavior into methods and examines how a Java method call is selected.
