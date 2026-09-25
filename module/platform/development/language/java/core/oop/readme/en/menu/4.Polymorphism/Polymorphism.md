# Polymorphism

## <a id="subtype-polymorphism">Subtype polymorphism</a>
Subtype polymorphism lets code depend on a supertype while working with many concrete implementations. The caller uses the common contract and does not need a type-specific branch for every implementation.

## <a id="dynamic-dispatch">Runtime dynamic dispatch</a>
For an overridden instance method, Java selects the implementation from the runtime class of the receiver object. The compiler has already chosen the method signature from the compile-time type; runtime dispatch chooses which override body executes.

```java
Animal a = new Dog();
a.speak(); // Dog override
```

## <a id="substitutability">Substitutability and behavioral expectations</a>
A subtype should satisfy the meaningful promises of the supertype: accepted states, postconditions, invariants, and side-effect expectations. Type compatibility alone does not guarantee good substitutability. A subclass that requires callers to special-case it weakens polymorphic design.
