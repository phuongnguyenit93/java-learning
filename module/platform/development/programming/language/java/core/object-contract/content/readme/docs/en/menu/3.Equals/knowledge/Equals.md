# The equals Contract

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

The compiler does not enforce these laws, but Java libraries **assume your objects honor them**. They apply to any non-null object reference used as the receiver of `equals`; `null` is not an object on which a method can be called.

Consider three distinct `String` objects that hold the same text. Run these statements in JShell or inside a `main` method:

```java
String a = new String("acct-8");
String b = new String("acct-8");
String c = new String("acct-8");

System.out.println(a == b);          // false: different objects
System.out.println(a.equals(a));     // true: reflexive
System.out.println(a.equals(b));     // true
System.out.println(b.equals(a));     // true: symmetric
System.out.println(b.equals(c));     // true
System.out.println(a.equals(c));     // true: transitive
System.out.println(a.equals(b));     // true again: consistent
System.out.println(a.equals(null));  // false
```

`new String(...)` creates a separate object each time, explaining why `a == b` is `false`. `String.equals` compares the characters, so all three objects are logically equal. The repeated call to `a.equals(b)` gives the same result because neither value changed. This is a *demonstration* of a correct implementation; a custom class must uphold the laws for **all** relevant inputs, not just three test objects.

### REFLEXIVE — AN OBJECT EQUALS ITSELF

If `x.equals(x)` could be false, basic reasoning inside equality-based APIs would break down. In the example, `a.equals(a)` must succeed even though `a` could have very different field values from another object.

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

In the `String` example, `a.equals(b)` and `b.equals(a)` both return `true`: the result cannot depend on which reference happens to be the receiver. Symmetry is especially easy to violate when inheritance lets a subtype add equality-relevant state; the last section works through that failure.

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

The three `String` objects show this chain directly: because `a` equals `b` and `b` equals `c`, `a` also equals `c`. Transitivity lets callers treat equality as a stable relation instead of a contradictory chain of pairwise comparisons. Checking only two objects cannot reveal every transitivity bug; a third object may be the crucial counterexample.

### CONSISTENT — SAME RELEVANT STATE, SAME ANSWER

Repeated comparisons must agree **provided no information used by `equals` has changed**. An implementation based on current time, randomness, or external I/O could make the same two objects equal in one call and unequal in the next, even if their fields never change.

```java
// Anti-pattern: equality depends on time
return Instant.now().getEpochSecond() % 2 == 0;
```

That is a broken design because callers cannot use the object predictably: the return value changes with the clock, not the logical values being compared. This sample is an intentionally incorrect method-body fragment, not an implementation to copy. The next section distinguishes a legitimate change to equality-relevant state from this kind of arbitrary inconsistency.

## <a id="equals-implementation">Implementing equals</a>

A value-style implementation commonly follows this flow. The class first decides which fields define the same logical value; the method then applies the same decision in both directions.

```text
1. same identity?             → true immediately
2. compatible type?          → otherwise false
3. compare equality state    → logical equality result
```

Here is a complete Java 21 example (`EqualsExample.java`). Two separately allocated IDs should compare equal when they hold the same identifier text:

```java
import java.util.Objects;

public class EqualsExample {
    public static void main(String[] args) {
        UserId first = new UserId("u-17");
        UserId sameValue = new UserId("u-17");
        UserId different = new UserId("u-18");

        System.out.println(first == sameValue);         // false
        System.out.println(first.equals(sameValue));    // true
        System.out.println(sameValue.equals(first));    // true
        System.out.println(first.equals(different));    // false
        System.out.println(first.equals(null));         // false
    }
}

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

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
```

The first check compares references and is `false`. For `first.equals(sameValue)`, `this == other` is also `false`, but the Java 21 `instanceof` pattern binds `that` to the other `UserId`. The final line compares `"u-17"` with `"u-17"` and returns `true`. With `different`, that same comparison finds `"u-17"` versus `"u-18"` and returns `false`. With `null`, the type test fails immediately, so no field is read from `null`. Reversing the receiver and argument follows the same steps and returns `true`.

> `UserId` is `final`, and `value` cannot change after construction. `Objects.requireNonNull` excludes a null identifier; `Objects.equals` then safely compares the field values (an ordinary `value.equals(that.value)` would also work under this constructor invariant). The small `hashCode` override keeps this *complete class* consistent with the rule that equal objects have equal hash codes. How hash codes are used and how to design them are the subjects of the next two chapters.

### OVERRIDE, DO NOT ACCIDENTALLY OVERLOAD

The `Object` contract uses this signature:

```java
public boolean equals(Object other)
```

Writing:

```java
public boolean equals(UserId other) {
    return other != null && value.equals(other.value);
}
```

creates an **overload**, not an override. Overload resolution uses the *declared parameter type at the call site*; changing that type can select a different method even when both references refer to the same object. For example, this deliberately broken class has only the overload:

```java
final class OverloadedId {
    private final String value;

    OverloadedId(String value) { this.value = value; }

    public boolean equals(OverloadedId other) {
        return other != null && value.equals(other.value);
    }
}
```

With `OverloadedId left = new OverloadedId("u-17")`, `OverloadedId right = new OverloadedId("u-17")`, and `Object asObject = right`:

```java
System.out.println(left.equals(right));    // true: overload selected
System.out.println(left.equals(asObject)); // false: Object.equals selected
```

The second call inherits `Object.equals(Object)` and therefore compares identity: the two instances are distinct. Collections and standard APIs commonly invoke the `Object` signature, so an accidental overload cannot implement logical equality reliably.

Keep `@Override` above `equals(Object)`, as in the complete `UserId` example, so the compiler checks the signature. If `equals(OverloadedId)` above had `@Override`, it would fail compilation because `Object` has no such method signature.

### WHICH FIELDS BELONG IN EQUALITY?

There is no rule that says every field must participate. Ask:

> Which state actually defines this value or entity in the domain?

A `User` may contain:

```text
userId       → stable domain identity
displayName  → mutable presentation state
lastLoginAt  → operational state
```

If `User` equality represents domain identity, including `lastLoginAt` would make the same user become “different” merely because they logged in. In such a design, two `User` instances with the same `userId` should compare equal even if one has a different `displayName` or `lastLoginAt`. For value objects such as `UserId`, the contained value itself is the equality state. Decide this policy first, then implement and test it consistently.

### MUTABLE EQUALITY STATE

The `equals` consistency rule says repeated comparisons should agree **while equality-relevant state remains unchanged**. It does not mean an object whose equality state changes must keep returning the old result.

For example, if equality includes a mutable `email` field, imagine comparing a user to another user whose email stays `"a@example.com"`:

```text
before change
user.email = "a@example.com"
→ user may equal another object with the same email

after change
user.email = "b@example.com"
→ that equality result may legitimately change
```

The new comparison result is legitimate: changing `email` changed one of the values explicitly used by the comparison. The design risk is therefore not “mutation automatically violates `equals`”. The risk is that the meaning of **which logical value/entity this object represents can move over time**, making reasoning, deduplication, caching, and later hash/sorted-key usage harder to keep predictable.

Prefer equality state that is stable for the lifetime in which callers rely on that identity/value meaning. The `equals/hashCode` chapter later shows the especially visible failure that occurs when equality/hash state changes after insertion into a hash-based collection.

### `instanceof` OR `getClass()`?

The two approaches express different policies.

```java
if (!(other instanceof UserId that)) return false;
```

tests whether `other` is an instance of `UserId` or a subclass, while:

```java
if (other == null || getClass() != other.getClass()) return false;
```

requires the exact runtime class. The `UserId` example is `final`, so no subclass can introduce a second equality policy: both approaches accept the same non-null type there. The difference matters for non-final classes, especially when a subtype adds fields.

There is no universal template that is right for every hierarchy. The chosen policy must preserve symmetry/transitivity and match the domain. `final` value classes or closed hierarchies often make the contract easier to maintain.

### `equals` AND NULL

For a non-null reference `x`:

```java
x.equals(null) // must be false
```

Calling a method on a null reference fails before `equals` can run:

```java
UserId x = null;
x.equals(new UserId("u-17")); // NullPointerException
```

`Objects.equals(a, b)` is useful when the caller needs null-safe equality: it returns `true` if both references are `null`, `false` if only one is `null`, and otherwise delegates to `a.equals(b)`. Thus `Objects.equals(null, null)` is `true` without contradicting the `x.equals(null)` rule, because no method is invoked on a null receiver.

## <a id="equals-inheritance-risk">Equality and Inheritance Risks</a>

Inheritance complicates equality when a subtype adds equality-relevant state. A parent might say two objects are equal because their coordinates match, while a child might demand that their colors match too. Both definitions may sound reasonable in isolation, but the *two objects must agree with each other* on whether they are equal.

Suppose a parent compares only `x`, while a child also compares `color`. The following complete example intentionally demonstrates an **incorrect** equality implementation (`InheritanceExample.java`):

> The example isolates the symmetry problem in `equals`. Its classes are deliberately unsuitable for production; a correct class that overrides `equals` must also override `hashCode` consistently. The next two chapters explain that additional requirement.

```java
import java.util.Objects;

public class InheritanceExample {
    public static void main(String[] args) {
        Point plain = new Point(1);
        ColoredPoint red = new ColoredPoint(1, "red");

        System.out.println(plain.equals(red)); // true
        System.out.println(red.equals(plain)); // false
    }
}

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

Both outputs follow from the code, but together they violate symmetry. Evaluate each direction separately:

1. `plain.equals(red)` executes **`Point.equals`**. `red instanceof Point` is `true`, because every `ColoredPoint` is also a `Point`. Both `x` values are `1`, so it returns `true` without examining `color`.
2. `red.equals(plain)` executes **`ColoredPoint.equals`**. `plain instanceof ColoredPoint` is `false`. The `&&` short-circuits; the color comparison never runs, and the method returns `false`.

The same pair receives contradictory answers depending on the receiver. Polymorphic method dispatch does not choose a single shared equality policy for the two objects; it chooses the implementation of the object on which `equals` is called.

It may be tempting to change only `ColoredPoint.equals` so it accepts plain `Point` objects as equal whenever their `x` values match. For example, *replacing just that method* with the following makes the two directions agree for a plain `Point` and a `ColoredPoint`:

```java
@Override
public boolean equals(Object other) {
    if (other instanceof ColoredPoint p) {
        return x == p.x && Objects.equals(color, p.color);
    }
    return other instanceof Point p && x == p.x;
}
```

However, the change creates a **transitivity** problem. With `Point plain = new Point(1)`, `ColoredPoint red = new ColoredPoint(1, "red")`, and `ColoredPoint blue = new ColoredPoint(1, "blue")`, the modified implementation gives:

| Comparison | Result | Why |
| --- | --- | --- |
| `red.equals(plain)` | `true` | The `Point` branch compares only `x = 1`. |
| `plain.equals(blue)` | `true` | `Point.equals` also compares only `x = 1`. |
| `red.equals(blue)` | `false` | Both are colored points, so `"red"` differs from `"blue"`. |

Since `red` equals `plain` and `plain` equals `blue`, transitivity requires `red` to equal `blue`. But it does not. This is why “allow every subtype to compare with its parent” is not a general fix when subtypes add equality-defining state.

### DESIGN LESSON

For complex value semantics, consider:

- composition instead of arbitrarily open inheritance;
- `final` value classes;
- sealed/closed hierarchies with an explicit equality policy;
- class-based equality when the domain requires exact-type identity.

For instance, if **both** `Point` and `ColoredPoint` follow an exact-runtime-class policy, `new Point(1)` and `new ColoredPoint(1, "red")` both reject each other before comparing fields. Two `ColoredPoint` objects can then compare `x` *and* `color` symmetrically within their own class. Applying an exact-type check in the child alone is insufficient if the parent still accepts subclasses through `instanceof`.

Making `Point` `final` is simpler when the domain has no need for subtypes, and composition lets a separate `ColoredPoint` contain a point without acquiring the parent's equality behavior. A sealed hierarchy narrows possible subtypes but still needs one deliberately consistent equality policy; `sealed` by itself does not enforce the contract.

The goal is not one universal `equals` template. The goal is a **coherent equality relation**. When reviewing a hierarchy, test comparisons *in both directions* and use three related instances to check transitivity, not just same-class happy paths.

### TRANSITION

`equals` answers whether two objects represent the same logical value. Hash-based collections also need a cheaper signal to **narrow candidates before calling equality**. That is the role of `hashCode`.
