# Program Control Flow

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

An unlabeled `break` exits the nearest applicable loop or switch statement; `continue` skips the rest of the current loop iteration and advances to the next iteration.

Labeled `break`/`continue` can express some nested-loop flows, but deeply nested control flow is often a signal that extraction into smaller methods would be clearer.

Choose loops by intent: classic `for` when the counter/update is part of the model, `while` for condition-driven repetition, and `do-while` when the body must execute at least once. Enhanced-for is revisited after arrays are introduced.

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

### Definite assignment across branches and loops

With multiple execution paths, the compiler must prove a local variable is assigned on **every path that can reach the read**:

```java
int x;
if (condition) {
    x = 1;
} else {
    x = 2;
}
System.out.println(x); // valid: every branch assigns x
```

A `while` body may execute zero times, so assignment inside it is not necessarily enough after the loop:

```java
int x;
while (condition) {
    x = 1;
}
// System.out.println(x); // x is not guaranteed to be assigned
```

This extends the definite-assignment rule introduced with variables and scope.

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

### Java 21 extends `switch` further

Java 21 also allows `switch` to classify reference values with patterns. At this milestone, it is enough to recognize that capability; **type patterns, runtime types, and pattern-variable flow scope** are explained later after the learner has the module's type-system mental model.

## <a id="control-flow-pitfalls">Control-flow Pitfalls</a>

Readability suffers with deeply nested branches, side effects inside long conditions, accidental switch fall-through, or many exits spread across several loop levels.

Guard clauses, extracted methods, data-driven design, or polymorphism may express the decision more clearly.

Guard clauses can flatten the happy path:

```java
if (user == null) return;
if (!user.isActive()) return;
process(user);
```

For condition-controlled loops, make the termination mechanism visible. Infinite loops may be intentional, but then the surrounding design should provide an explicit stop/interrupt mechanism where appropriate.

The next chapter introduces arrays, where enhanced-for can be studied against a concrete sequence type.
