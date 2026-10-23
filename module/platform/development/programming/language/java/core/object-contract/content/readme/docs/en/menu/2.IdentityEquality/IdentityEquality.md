# Object Identity vs Logical Equality

## <a id="identity-vs-equality">Identity and Equality</a>

### WHAT

Imagine receiving a user identifier from two separate requests. Each request creates a `UserId` object containing `"U-100"`. The two objects describe the same identifier, but they were constructed separately. Should Java consider them "the same"? That depends on which question the code is asking.

**Object identity** answers:

> Do these two references identify the exact same object?

**Logical equality** answers a different question:

> Should two objects count as the same value or represent the same domain entity under the type's rules?

Java exposes two different mechanisms:

```text
==
→ when comparing references: object identity

equals(...)
→ method a type can override to define logical equality
```

The distinction matters even when both objects hold exactly the same text. **Having equal data does not make two separately created objects one object**, and having the same object does not tell us which fields an application should use to compare two *different* instances.

### WHY THE DISTINCTION MATTERS

Without this distinction, code can be syntactically valid but wrong for the domain. Suppose we write the following statements after declaring a suitable `UserId` type:

```java
UserId a = new UserId("U-100");
UserId same = a;
UserId b = new UserId("U-100");
```

There are two independent questions:

```text
a == same  → true: assigning a to same copies the reference
a == b     → false: the two new expressions create separate objects

a.equals(b) → depends on the equality rules implemented by UserId
```

The first two results follow from how references were assigned and objects were created. The third cannot be decided from the matching `"U-100"` text alone: an ordinary class inherits identity-based equality unless it overrides `equals`, whereas a suitable value type can define equality by identifier value.

This choice affects everyday code. If distinct `UserId("U-100")` objects should count as one key, using reference identity to decide whether they are duplicates would be incorrect. `HashSet` and `HashMap` use `equals` together with `hashCode` for this purpose; `TreeSet` instead determines duplicates through its ordering comparison. Those collections cannot infer the intended domain rule from the field name.

### REFERENCE, OBJECT, AND VALUE

A reference variable holds a reference to an object; it is not the object itself. After the three statements above, the following diagram shows the *references* (left), the two *objects* (right), and the same *stored identifier value* in each object:

```text
UserId a ───────┐
                ├──> UserId("U-100")
UserId same ────┘

UserId b ──────────> UserId("U-100")
```

`same = a` creates no new `UserId`; both variables lead to one instance. The second `new` creates another instance for `b`, so `a` and `b` have different identities. The strings inside them happen to match, but that is a separate observation. This object/reference/value separation is the foundation for interpreting both `==` and `equals` correctly.

## <a id="reference-equality">Reference Equality with ==</a>

For two reference operands, `==` checks whether both references identify the same object (including an array), or whether both are `null`. It does **not** inspect fields or call `equals`.

```java
String a = new String("java");
String b = new String("java");
String same = a;

System.out.println(a == b);       // false
System.out.println(a == same);    // true
System.out.println(a.equals(b));  // true
```

The first two `new String(...)` calls create distinct `String` objects. Assigning `a` to `same` shares one reference without constructing another string. Consequently, `==` is false for `a`/`b` and true for `a`/`same`; `String.equals` returns true for `a`/`b` because their character sequences match. **The same inputs can therefore yield different results because the operations ask different questions.**

### WHAT DOES DEFAULT `Object.equals()` DO?

If a class does **not** override `equals`, it inherits `Object.equals()`, whose equality semantics are identity-based.

```java
final class UserId {
    private final String value;

    UserId(String value) {
        this.value = value;
    }
}

UserId a = new UserId("U-100");
UserId b = new UserId("U-100");
UserId same = a;

System.out.println(a == b);       // false
System.out.println(a.equals(b));  // false: default Object.equals
System.out.println(a.equals(same)); // true: both references identify a
```

Trace the method call: `a.equals(b)` uses the inherited implementation because this `UserId` declares no override. `Object.equals` asks whether its receiver and argument are the same instance; they are not. For `a.equals(same)`, both references identify the first instance, so the inherited method returns `true`. The identical `value` fields do not participate in either decision.

That is why domain classes override `equals` when they need logical equality: **Java cannot infer which fields define "the same value" for your type**. The next H2 shows one value-based design; the next chapter teaches the laws and implementation choices for overriding `equals`.

### REFERENCE VS PRIMITIVE VALUES AND NULL

`==` also compares primitive values, but that is **value comparison**, not object identity:

```java
int first = 100;
int second = 100;
System.out.println(first == second); // true: equal int values

UserId absent = null;
UserId alsoAbsent = null;
System.out.println(absent == alsoAbsent); // true: both are null
System.out.println(a == null);            // false: a references an object
```

`null` is the absence of an object reference, not a `UserId` object. The `== null` check is valid, but `absent.equals(a)` would throw `NullPointerException` because no receiver object exists. When a caller needs null-safe logical comparison, `Objects.equals(a, b)` handles null before delegating to an object's `equals` method.

This boundary also applies to arrays: two separately allocated arrays remain different references under `==`, even if their elements match. A method such as `Arrays.equals` compares their elements instead.

### WHEN IS `==` THE RIGHT TOOL?

Identity comparison is appropriate when identity itself matters, for example:

- enum constants;
- singleton instances;
- sentinel objects;
- checking whether two references deliberately share one object.

It is usually the wrong operation for comparing the contents of value-like objects such as `String`, money values, or identifier value objects. A null check, on the other hand, is a deliberate reference check and belongs to the identity side of this distinction.

> String literals and compile-time constant string expressions are interned according to Java's string-literal semantics, so equal literals can deliberately refer to the same pooled instance. Runtime-created strings do not gain that guarantee automatically. This still does not make `==` a correct content-comparison operator for `String`.

## <a id="value-object-equality">Value Object Equality</a>

A **value object** represents a meaningful value. Its equality policy normally depends on that value rather than on which allocation created the object. A `UserId` used as an identifier value is a good example: independent requests can construct separate instances, yet both should describe `"U-100"`.

Two `Money(100, "USD")` instances may be logically equal even if they were constructed independently.

### A VALUE-BASED `UserId` TRACE

In this *separate example*, `UserId` is now defined as a Java 21 `record` with one component. Java generates `equals` and `hashCode` for records based on their components; here the component is the identifier string. This gives us a complete example of value-based equality without implementing the override that the next chapter will teach for ordinary classes.

```java
public class EqualityExample {
    record UserId(String value) {}

    public static void main(String[] args) {
        UserId a = new UserId("U-100");
        UserId same = a;
        UserId b = new UserId("U-100");
        UserId other = new UserId("U-200");

        System.out.println(a == same);      // true
        System.out.println(a == b);         // false
        System.out.println(a.equals(b));    // true
        System.out.println(a.equals(other)); // false
    }
}
```

Follow each comparison in order:

1. `same = a` copies a reference, so `a == same` is `true`.
2. `b` comes from another `new`, so `a == b` is `false`, regardless of the stored value.
3. `a.equals(b)` compares the record component values. Both `value` strings contain `"U-100"`, so the result is `true` despite the two objects having different identities.
4. `other` contains `"U-200"`, so `a.equals(other)` is `false` even though both are `UserId` records.

An ordinary class like the preceding `UserId` does **not** automatically gain this record behavior. Its author must define logical equality explicitly if the domain requires it. A record is appropriate here because the identifier value is fully captured by its single component; a record with additional components would, by default, include those components in its generated equality.

### VALUE OBJECT VS ENTITY

A useful model is:

```text
Value object
→ "what value does it carry?"

Entity
→ "which domain entity is it?"
```

`Money(100, "USD")` is commonly value-equal by amount **and** currency: `Money(100, "USD")` and `Money(100, "EUR")` should not compare equal merely because their numeric amounts match. A `User` entity may instead be equal by a stable `userId`, not by every mutable field such as display name or last-login time. Two `User` objects could therefore represent the same domain user without being the same in-memory object.

The important rule is to choose equality from **domain meaning**, not by blindly including every field an IDE can see. The record example expresses a one-component *value* policy; entity equality is a separate design decision.

### EQUALITY AND ORDERING STATE SHOULD BE STABLE IN COLLECTIONS

A sound equality design usually:

- uses the components that truly define the value or domain identity;
- preserves reflexive, symmetric, transitive, and consistent behavior;
- keeps `hashCode` behavior consistent with equality, so objects considered equal always produce the same hash code.

When an object is a **`HashMap` key or `HashSet` element**, keep the fields used by `equals` and `hashCode` stable: hash-based lookup depends on both methods and can lose track of an entry when its hash/equality state changes after insertion. If `UserId` is defined solely by `value`, equality and hashing should both use that `value`. A record's components cannot be reassigned after construction, which makes this particular example easier to reason about.

When an object is a **`TreeMap` key or `TreeSet` element**, keep the fields used by its natural order (`compareTo`) or the supplied `Comparator` stable instead. For example, a comparator could order `User` objects by mutable display name even when `equals` uses only an immutable user ID. Changing that name while the object remains in a sorted collection can break ordered lookup despite unchanged logical equality. The relevant fields may overlap, but **equality state and ordering state do not have to be the same**. Later chapters develop these separate contracts and risks.

### TRANSITION

Identity follows references and allocations; logical equality follows the type's chosen meaning. We can now predict why a pair of `UserId` objects can be `== false` yet `equals true`, and why an ordinary class without an override instead gives `false` for both. The next chapter defines the exact laws an `equals` implementation must preserve so Java libraries can trust that decision.
