# Abstract Classes — Shared State and Partial Implementation

The previous chapter separated two needs: **the contract visible to consumers** and **the state/behavior a family of implementations wants to share**. Abstract classes are strongest when related subclasses genuinely share the second kind of concern.

## <a id="abstract-class-model">What Is an Abstract Class?</a>

### CONCEPT

An `abstract class` cannot be instantiated directly, but it may still contain:

- instance state through fields;
- constructors;
- concrete methods with bodies;
- abstract methods without bodies.

An abstract class does **not** have to declare an abstract method. Marking the class `abstract` first means that the class itself is not a directly instantiable concrete type.

That makes an abstract class useful as a **partial implementation** for a closely related family of subclasses.

```java
abstract class BasePayment {
    private final String provider;

    protected BasePayment(String provider) {
        this.provider = provider;
    }

    String provider() {
        return provider;
    }

    public abstract void pay(int amount);
}
```

`BasePayment` owns shared state and behavior while requiring concrete subclasses to complete `pay(...)`.

### WHY

If several related classes genuinely share state, initialization rules, invariants, and common behavior, duplicating those rules across subclasses creates repetition and makes shared constraints harder to maintain. An abstract class centralizes the common part while leaving explicit extension points.

The next chapter places a `PaymentMethod` interface above this implementation so the **contract consumers depend on** stays separate from the **shared implementation** reused by one class branch.

## <a id="abstract-method">Abstract Methods</a>

### CONCEPT

An abstract method declares a method signature without a body:

```java
abstract void pay(int amount);
```

A concrete subclass must ensure that every abstract requirement has a compatible implementation before the type can be instantiated. The subclass does not have to declare the method itself when it already inherits a compatible concrete implementation from an intermediate superclass.

### MECHANICS

Abstract methods follow normal Java overriding rules:

- visibility must remain compatible;
- return types must remain compatible, including covariant returns where allowed;
- checked exceptions must follow overriding rules;
- `@Override` lets the compiler verify the intended relationship.

`abstract` does not introduce a special dispatch model. Once a concrete subclass supplies an implementation, ordinary instance-method dynamic dispatch applies.

## <a id="abstract-constructor">Abstract Class Constructors</a>

Even though an abstract class cannot be instantiated directly, its constructor still runs while a concrete subclass object is being created:

```java
class CardPayment extends BasePayment {
    CardPayment() {
        super("card");
    }

    @Override
    public void pay(int amount) { ... }
}
```

The `BasePayment` constructor is not inherited by `CardPayment`. It participates in the constructor chain and initializes the `BasePayment` portion of the `CardPayment` object before subclass construction completes.

Avoid calling overridable methods from constructors unless there is a strong reason. Subclass behavior may run before subclass-specific state has been initialized.

## <a id="abstract-class-limits">Abstract Class Limits</a>

`abstract` only prevents direct instantiation. An abstract class is still a valid reference type:

```java
BasePayment payment = new CardPayment();
```

The larger structural constraint is that a Java class may extend only **one class**. If a capability must cross unrelated class hierarchies, forcing every implementation under one abstract base class creates unnecessary coupling.

That leads directly to interfaces: **what if we need a common contract without committing every implementation to one class hierarchy?**
