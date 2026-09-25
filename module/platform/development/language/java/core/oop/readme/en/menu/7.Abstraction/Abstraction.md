# Abstraction

After encapsulation, subtyping, polymorphism, and composition, we can ask the final question: **what does a caller actually need to know to use a capability?** Abstraction keeps that essential view and removes irrelevant implementation detail from the caller's mental model.

## <a id="abstraction-model">What Is Abstraction?</a>

### WHAT — what is abstraction?

An abstraction is a model or contract that exposes only the concepts and behavior a caller needs while keeping unrelated implementation details behind a boundary.

For checkout, a caller may need only:

```java
interface Pricing {
    int price(int base);
}
```

The caller does not need to know whether an implementation calculates discounts from a rule table, a database, or a formula if those details are outside the contract.

### RELATION — abstraction and encapsulation

The concepts complement each other:

```text
Abstraction
→ decide what the caller needs to see

Encapsulation
→ control what the caller must not freely access or mutate
```

An API may hide fields with `private` while still exposing a poor abstraction through too many low-level operations. Conversely, a very small contract may still leak implementation details through undocumented errors or performance behavior.

### HOW — abstraction is not the `abstract` keyword

Interfaces and abstract classes are common Java mechanisms for expressing abstractions, but a concrete class or function can also form a good abstraction when its contract is clear.

The `abstract-interface` module goes deeper into choosing interfaces vs abstract classes. Here the focus is the **design role**, not the keyword.

## <a id="program-to-abstraction">Program to Abstraction</a>

### WHAT

“Program to abstraction” means consumers depend on a stable role or contract rather than an unnecessary concrete implementation.

```java
final class Checkout {
    private final Pricing pricing;

    Checkout(Pricing pricing) {
        this.pricing = pricing;
    }
}
```

`Checkout` knows `Pricing`; it does not need to know specifically about `Discount` or `Regular`.

### WHY — reduce what consumers need to know

When an implementation changes, the consumer may remain unchanged as long as the contract still holds. That is what allows polymorphism and composition to create real flexibility.

### TRADE-OFF — do not create an interface for every class

An abstraction layer should represent a meaningful role/contract, variation point, test boundary, or architectural boundary. Creating `FooInterface` for `FooImpl` merely because “OOP best practice says so” can add indirection without reducing real coupling.

### PRACTICE — inspect dependency direction

Ask the consumer: **which capability does it need, and is it depending on a concrete type only for convenience?** If it needs only a stable subset of behavior, that may be a candidate for a better abstraction.

## <a id="abstraction-leak">Leaky Abstractions</a>

### WHAT — when does an abstraction leak?

An abstraction leaks when callers must understand implementation details to use the contract correctly.

Examples:

- an API says “save”, but callers must know the concrete provider to handle errors correctly;
- a collection hides storage, but performance changes dramatically by implementation;
- a resource abstraction hides lifecycle so thoroughly that callers do not know they must close it;
- ordering or thread-safety assumptions are omitted even though they affect correctness.

### WHY — no abstraction can hide everything

The goal is not to hide every detail. Constraints that materially affect correctness or an important performance contract should be exposed or documented. Irrelevant implementation details should remain hidden.

### RELATION — connect the whole module

OOP can now be seen as one continuous flow:

```text
Object owns state + responsibility
→ Encapsulation keeps state transitions valid
→ Subtyping creates a common type relationship
→ Polymorphism provides one contract with many behaviors
→ Overriding + dynamic dispatch are key Java runtime mechanisms
→ Composition/delegation vary behavior through collaborators instead of deeper hierarchies
→ Abstraction keeps the essential contract and reduces caller knowledge
```

`Overloading` appears in this module to distinguish compile-time method selection from runtime polymorphism; it is not a separate OOP pillar.

### PRACTICE — end-of-module review criteria

When reviewing an OOP design, do not count classes or interfaces. Ask:

1. are responsibilities clear;
2. does state/invariant have a clear owner;
3. are subtypes genuinely substitutable;
4. is inheritance vs composition chosen for the right reason;
5. do callers depend on unnecessary implementation details.

If you can answer those five questions, you have a more useful OOP mental model than memorizing only “the four pillars”.
