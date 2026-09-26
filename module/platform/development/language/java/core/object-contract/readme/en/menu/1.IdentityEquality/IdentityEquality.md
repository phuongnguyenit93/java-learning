# Identity and Equality

This module is about a set of **contracts that other Java APIs rely on to understand your objects**. `equals`, `hashCode`, `toString`, `Comparable`, and `Comparator` are not isolated utility methods. They influence collection lookup, sorting, logging, and how many frameworks treat domain objects.

Learning roadmap:

```text
Same object or same logical value?
Identity vs Equality
        ↓
How should a class define logical equality?
equals
        ↓
How do hash-based collections find objects efficiently?
hashCode
        ↓
Why must equals and hashCode agree?
equals + hashCode
        ↓
How should an object describe itself for humans/tools?
toString
        ↓
How can a type define one natural order?
Comparable
        ↓
How can callers define alternative orderings?
Comparator
```

Throughout the module, keep a small value object such as `UserId` and a domain object such as `Book` in mind. We will follow them through `HashSet`, `HashMap`, `TreeSet`, and sorting operations. The goal is not to memorize generated methods, but to understand **which promises Java libraries expect your objects to keep**.

## <a id="identity-vs-equality">Identity and Equality</a>

### WHAT

**Identity** answers:

> Do these two references identify the exact same object?

**Logical equality** answers a different question:

> Should two different objects be treated as the same value or the same domain entity?

Java exposes two different mechanisms:

```text
==
→ for references: identity comparison

equals(...)
→ overridable logical-equality method
```

### WHY THE DISTINCTION MATTERS

Without this distinction, code can be syntactically valid but semantically wrong for the domain.

```java
UserId a = new UserId("U-100");
UserId b = new UserId("U-100");
```

There are two independent questions:

```text
Are a and b the exact same object?
→ identity

Do a and b represent the same user id?
→ logical equality
```

Collections such as `HashSet`, `HashMap`, and `TreeSet` cannot guess what “same” means in your domain. They rely on contracts supplied by the type or by the caller.

### REFERENCE, OBJECT, AND VALUE

A reference variable is not the object itself. A useful mental model is:

```text
UserId a ───────┐
                ├──> UserId("U-100")
UserId same ────┘

UserId b ──────────> UserId("U-100")
```

`a` and `same` share identity because they identify the same object. `a` and `b` have different identities even though their data is the same.

## <a id="reference-equality">Reference Equality with ==</a>

For references, `==` only checks whether both references identify the same object, or whether both are `null`.

```java
String a = new String("java");
String b = new String("java");
String same = a;

System.out.println(a == b);       // false
System.out.println(a == same);    // true
System.out.println(a.equals(b));  // true
```

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

System.out.println(a == b);       // false
System.out.println(a.equals(b));  // false: default Object.equals
```

That is why domain classes override `equals` when they need logical equality: **Java cannot infer which fields define “the same value” for your type**.

### WHEN IS `==` THE RIGHT TOOL?

Identity comparison is appropriate when identity itself matters, for example:

- enum constants;
- singleton instances;
- sentinel objects;
- checking whether two references deliberately share one object.

It is usually the wrong operation for value-like objects such as `String`, money values, or identifier value objects.

> A string pool may cause some string literals to share identity. That does not make `==` a correct content-comparison operator for `String`.

## <a id="value-object-equality">Value Object Equality</a>

A value object is usually identified by its **meaningful value**, not by object identity.

Two `Money(100, "USD")` instances may be logically equal even if they were constructed independently.

### VALUE OBJECT VS ENTITY

A useful model is:

```text
Value object
→ "what value does it carry?"

Entity
→ "which domain entity is it?"
```

`Money(100, "USD")` is commonly value-equal by amount and currency. A `User` may instead be equal by a stable `userId`, not by every mutable field such as display name or last-login time.

The important rule is to choose equality from **domain meaning**, not by blindly including every field an IDE can see.

### EQUALITY STATE SHOULD BE STABLE

A sound equality design usually:

- uses the components that truly define the value or domain identity;
- preserves reflexive, symmetric, transitive, and consistent behavior;
- uses the same equality-relevant state for `hashCode`;
- avoids mutating equality state while the object is used as a hash/sorted key.

If `UserId` is defined solely by `value`, both equality and hashing should be based on that same `value`.

### TRANSITION

We now know that **logical equality is a domain/type decision rather than reference identity**. The next chapter defines the exact laws `equals` must preserve so Java libraries can trust that decision.
