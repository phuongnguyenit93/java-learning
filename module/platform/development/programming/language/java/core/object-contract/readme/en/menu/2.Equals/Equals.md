# equals

Once identity and logical equality are separated, the next question is: **if a class defines when two objects are logically equal, which laws must `equals` obey?**

`equals` is not merely a boolean method. It is a contract that collections, frameworks, and caller code rely on when reasoning about objects.

## <a id="equals-contract">The equals Contract</a>

A correct `equals` implementation preserves several core properties:

```text
reflexive
x.equals(x) is true

symmetric
x.equals(y) and y.equals(x) agree

transitive
if x.equals(y) and y.equals(z) are true,
then x.equals(z) is true

consistent
the result stays stable while relevant state is unchanged

null
x.equals(null) is false
```

The compiler does not enforce these laws, but Java libraries **assume your objects honor them**.

### REFLEXIVE — AN OBJECT EQUALS ITSELF

If `x.equals(x)` could be false, basic reasoning inside equality-based APIs would break down.

This fast path is both correct and cheap:

```java
if (this == other) {
    return true;
}
```

### SYMMETRIC — BOTH DIRECTIONS MUST AGREE

If:

```text
a.equals(b) == true
```

then:

```text
b.equals(a) == true
```

Symmetry is especially easy to violate when inheritance lets a subtype add equality-relevant state.

### TRANSITIVE — EQUALITY MUST NOT BREAK THROUGH AN INTERMEDIATE VALUE

If:

```text
x.equals(y) == true
y.equals(z) == true
```

then:

```text
x.equals(z) == true
```

Transitivity lets callers treat equality as a stable relation instead of a contradictory chain of pairwise comparisons.

### CONSISTENT — SAME RELEVANT STATE, SAME ANSWER

An implementation based on current time, randomness, or external I/O could make the same two objects equal in one call and unequal in the next.

```java
// Anti-pattern: equality depends on time
return Instant.now().getEpochSecond() % 2 == 0;
```

That is a broken design because callers cannot use the object predictably.

## <a id="equals-implementation">Implementing equals</a>

A value-style implementation commonly follows this flow:

```text
1. same identity?             → true immediately
2. compatible type?          → otherwise false
3. compare equality state    → logical equality result
```

```java
import java.util.Objects;

final class UserId {
    private final String value;

    UserId(String value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserId that)) return false;
        return Objects.equals(value, that.value);
    }
}
```

### OVERRIDE, DO NOT ACCIDENTALLY OVERLOAD

The `Object` contract uses this signature:

```java
public boolean equals(Object other)
```

Writing:

```java
public boolean equals(UserId other) {
    ...
}
```

creates an **overload**, not an override. Calls made through `Object`, collections, or standard APIs may still use `Object.equals()` rather than the method you intended to replace.

Prefer keeping `@Override` on the method:

```java
@Override
public boolean equals(Object other) {
    ...
}
```

so the compiler catches a wrong signature immediately.

### WHICH FIELDS BELONG IN EQUALITY?

There is no rule that says every field must participate. Ask:

> Which state actually defines this value or entity in the domain?

A `User` may contain:

```text
userId       → stable domain identity
displayName  → mutable presentation state
lastLoginAt  → operational state
```

If `User` equality represents domain identity, including `lastLoginAt` would make the same user become “different” merely because they logged in.

### `instanceof` OR `getClass()`?

The two approaches express different policies.

```java
if (!(other instanceof UserId that)) return false;
```

allows compatible subtypes to participate, while:

```java
if (other == null || getClass() != other.getClass()) return false;
```

requires the exact runtime class.

There is no universal template that is right for every hierarchy. The chosen policy must preserve symmetry/transitivity and match the domain. `final` value classes or closed hierarchies often make the contract easier to maintain.

### `equals` AND NULL

For a non-null reference `x`:

```java
x.equals(null) // must be false
```

Calling a method on a null reference fails before `equals` can run:

```java
UserId x = null;
x.equals(other); // NullPointerException
```

`Objects.equals(a, b)` is useful when the caller needs null-safe equality.

## <a id="equals-inheritance-risk">Equality and Inheritance Risks</a>

Inheritance complicates equality when a subtype adds equality-relevant state.

Suppose a parent compares only `x`, while a child also compares `color`:

> The example below intentionally isolates the symmetry problem in `equals`. In a complete class, every class that overrides `equals` must still implement a `hashCode` consistent with its equality state.

```java
class Point {
    final int x;

    Point(int x) {
        this.x = x;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Point p && x == p.x;
    }
}

class ColoredPoint extends Point {
    final String color;

    ColoredPoint(int x, String color) {
        super(x);
        this.color = color;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ColoredPoint p
                && x == p.x
                && Objects.equals(color, p.color);
    }
}
```

This can produce:

```text
new Point(1).equals(new ColoredPoint(1, "red"))
→ true

new ColoredPoint(1, "red").equals(new Point(1))
→ false
```

Symmetry is broken.

### DESIGN LESSON

For complex value semantics, consider:

- composition instead of arbitrarily open inheritance;
- `final` value classes;
- sealed/closed hierarchies with an explicit equality policy;
- class-based equality when the domain requires exact-type identity.

The goal is not one universal `equals` template. The goal is a **coherent equality relation**.

### TRANSITION

`equals` answers whether two objects represent the same logical value. Hash-based collections also need a cheaper signal to **narrow candidates before calling equality**. That is the role of `hashCode`.
