# Abstract Class

This module starts from a practical Java design question: **how do we define a shared contract while still allowing multiple implementations?** Java gives us two major mechanisms for that job: abstract classes and interfaces.

An abstract class fits naturally when related subclasses share not only a contract, but also **state and part of the implementation**. An interface fits better when we want to describe a capability or role that can span unrelated class hierarchies.

Learning roadmap:

```text
Abstract Class
→ shared state + partial implementation
        ↓
Interface
→ role/contract independent of one class hierarchy
        ↓
Abstract Class vs Interface
→ choose the mechanism that matches the relationship
        ↓
Interface Inheritance
→ compose smaller contracts into larger ones
        ↓
Default / Static / Private Methods
→ evolve interfaces and reuse contract-side behavior
        ↓
Multiple Interface Inheritance
→ resolve inherited default-method conflicts
```

## <a id="abstract-class-model">What Is an Abstract Class?</a>

### CONCEPT

An `abstract class` cannot be instantiated directly, but it may still contain:

- instance state;
- constructors;
- concrete methods;
- abstract methods.

That makes it useful as a **partial implementation** for a closely related family of subclasses.

```java
abstract class PaymentMethod {
    private final String provider;

    protected PaymentMethod(String provider) {
        this.provider = provider;
    }

    String provider() {
        return provider;
    }

    abstract void pay(int amount);
}
```

`PaymentMethod` owns shared state and behavior while requiring each concrete subtype to complete `pay(...)`.

### WHY

If several related classes genuinely share initialization rules, invariants, and common behavior, duplicating that code across subclasses creates avoidable coupling and repetition. An abstract class keeps the common part in one place while leaving explicit extension points.

## <a id="abstract-method">Abstract Methods</a>

An abstract method declares a method signature without a body:

```java
abstract void pay(int amount);
```

A concrete subclass must provide a compatible implementation unless an inherited concrete method already satisfies the contract.

Abstract methods still follow the normal overriding rules for visibility, return types, and checked exceptions. `@Override` is useful because it lets the compiler verify the relationship.

There is no special dispatch model for abstract methods: once a concrete subclass overrides the method, ordinary instance-method dynamic dispatch applies.

## <a id="abstract-constructor">Abstract Class Constructors</a>

Even though an abstract class cannot be instantiated directly, its constructor still runs when a concrete subclass is created.

```java
class CardPayment extends PaymentMethod {
    CardPayment() {
        super("card");
    }

    @Override
    void pay(int amount) { ... }
}
```

The superclass constructor establishes the superclass portion of the object before subclass construction completes.

Avoid calling overridable methods from constructors unless there is a strong reason: subclass behavior may run before subclass state has been initialized.

## <a id="abstract-class-limits">Abstract Class Limits</a>

`abstract` only prevents direct instantiation. An abstract class is still a valid reference type:

```java
PaymentMethod payment = new CardPayment();
```

The more important limitation is that a Java class can extend only **one class**. If a capability must cross unrelated class hierarchies, an abstract base class is often too restrictive.

That leads directly to interfaces: **what if we need a common contract without committing every implementation to one class hierarchy?**
