# What Is an Object Contract?

## <a id="object-contract-purpose">What Is an Object Contract?</a>

In Java, an **object contract** is not a keyword, annotation, special interface, or one formal language construct. In this module, the phrase is an **umbrella term** for documented behavioral promises that objects and comparison policies must keep so other code can use them consistently.

For example, when a class says two objects are equal through `equals`, Java expects `hashCode` to agree with that decision. When a type defines natural ordering through `Comparable`, or a caller supplies a `Comparator`, sorting algorithms and sorted collections expect the comparison relation to be consistent and transitive. `toString` provides a different contract: a useful textual representation for people and diagnostic tools.

This module is about a set of **contracts that other Java APIs rely on to understand your objects**. `equals`, `hashCode`, `toString`, `Comparable`, and `Comparator` are not isolated utility methods. They influence collection lookup, sorting, logging, and how many frameworks treat domain objects.

The compiler can usually verify method types and signatures, but it cannot prove that your implementation preserves the meaning of these contracts. Code may therefore compile while runtime behavior is wrong: a `HashSet` may retain values the domain considers duplicates, a `HashMap` lookup may fail to find an expected key, a sorted collection may treat two values as occupying the same ordering position while `equals` says otherwise, or logs and debugger output may become misleading. Library and framework code that stores, compares, sorts, or diagnoses objects relies on these promises instead of rediscovering your domain rules.

Developers therefore need to understand **what each contract means, why it exists, how the contracts relate, and what breaks when one is violated**. The module first establishes the purpose of object contracts, then separates object identity from logical equality before moving through `equals`, `hashCode`, their shared contract, `toString`, ordering through `Comparable` and `Comparator`, and finally an end-to-end synthesis.

Learning roadmap:

```text
What is an object contract, and why do Java APIs need these promises?
Object Contract Purpose
        ↓
Same object or same logical value?
Object Identity vs Logical Equality
        ↓
How should a class define logical equality?
The equals Contract
        ↓
How do hash-based collections find objects efficiently?
hashCode and Hash-Based Lookup
        ↓
Why must equals and hashCode agree?
The equals/hashCode Contract
        ↓
How should an object describe itself for humans/tools?
Text Representation with toString
        ↓
How can a type define one natural order?
Natural Ordering with Comparable
        ↓
How can callers define alternative orderings?
Alternate Ordering with Comparator
        ↓
How do all of these promises interact in real Java APIs?
Putting the Object Contracts Together
```

Throughout the module, `UserId` is the primary example for identity, equality, and hashing, while `Book` is used when multiple ordering/representation views are useful. `BigDecimal` appears only as a documented cross-module example where equality and natural ordering intentionally differ. The goal is not to memorize generated methods, but to understand **which promises Java libraries expect your objects to keep**.
