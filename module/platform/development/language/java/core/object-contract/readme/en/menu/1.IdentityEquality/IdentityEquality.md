# Identity and Equality

This module is about a set of **contracts that other Java APIs rely on to understand your objects**. `equals`, `hashCode`, `toString`, `Comparable`, and `Comparator` are not isolated utility methods. They directly influence collections, sorting, logging, and many frameworks.

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

Throughout the module, imagine a value object such as `Money`, `UserId`, or `BookKey` moving through `HashSet`, `HashMap`, `TreeSet`, and sorting operations.

## <a id="identity-vs-equality">Identity vs Equality</a>

### CONCEPT

**Identity** asks:

> Do these references identify the exact same object?

**Logical equality** asks:

> Should these different objects represent the same value or entity according to the domain contract?

Java uses:

```text
==
→ reference identity

equals(...)
→ overridable hook for logical equality
```

Two objects may contain the same data while still having different identities.

## <a id="reference-equality">Reference Equality with ==</a>

For references, `==` only checks whether both references identify the same object, or both are `null`.

```java
String a = new String("java");
String b = new String("java");

a == b      // false
a.equals(b) // true
```

Identity comparison is appropriate for cases such as enum constants, singleton identities, or sentinel objects. It is usually the wrong operation for value-like objects such as strings, money values, or IDs.

## <a id="value-object-equality">Value Object Equality</a>

A value object is usually identified by its **meaningful value**, not object identity.

Two `Money(100, "USD")` instances may be logically equal even if they were constructed independently.

A sound equality design usually:

- uses the components that truly define the value;
- stays stable while the object is used as a key;
- preserves symmetry and transitivity;
- avoids mutable equality state when the object participates in hash/sorted collections.

The next chapter focuses on the exact contract of `equals`.
