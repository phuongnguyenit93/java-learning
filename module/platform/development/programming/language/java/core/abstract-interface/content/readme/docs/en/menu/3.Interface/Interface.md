# Interfaces as Behavioral Contracts

Abstract classes work well when related subclasses share state and implementation. Many designs, however, only need to say that a type **has a capability or obeys a contract**, regardless of which class hierarchy it belongs to. Interfaces model that need directly.

## <a id="interface-contract">What Is an Interface?</a>

### CONCEPT

An `interface` names a **behavioral role or contract** that different kinds of classes may implement. In Java 21, ordinary classes, enum classes, record classes, and generated proxy classes can implement interfaces when appropriate.

```java
interface PaymentMethod {
    void pay(int amount);
}
```

A class implementing `PaymentMethod` promises that consumers can request `pay(...)` through the `PaymentMethod` type without knowing the concrete class behind it.

### WHY

Interfaces reduce unnecessary dependence on concrete implementations:

```java
void checkout(PaymentMethod payment) {
    payment.pay(100);
}
```

`checkout(...)` depends on the `PaymentMethod` contract rather than directly on `CardPayment`, `WalletPayment`, or another specific implementation.

## <a id="interface-fields">Interface Fields</a>

Fields declared in an interface are implicitly:

```text
public static final
```

```java
interface Limits {
    int MAX_RETRY = 3;
}
```

`MAX_RETRY` in this example is a constant variable attached to the interface type, not per-instance state of implementing objects. More generally, every interface field is implicitly `public static final`; only fields that also satisfy Java's constant-variable rules are compile-time constants.

An interface field must have an initializer in its declaration. Interface fields are `static final`; they are not assigned later by per-instance constructors:

```java
interface InvalidLimits {
    int MAX_RETRY; // compile error: variable MAX_RETRY not initialized
}
```

`final` prevents the field variable itself from being assigned a different value after initialization. If that value is a reference to a mutable object, the referenced object can still change state. Avoid exposing mutable global state through interface fields.

## <a id="interface-method-kinds">Interface Method Kinds</a>

Modern Java interfaces may contain several method kinds:

| Kind | Role |
| --- | --- |
| abstract instance method | contract the implementation must satisfy |
| `default` method | inheritable default instance behavior |
| `static` method | behavior attached to the interface type |
| `private` method | internal helper; may be instance or `static` |

An abstract instance method with no explicit access modifier is implicitly `public abstract`:

```java
interface PaymentMethod {
    void pay(int amount); // public abstract
}
```

An implementing class therefore cannot reduce the method's visibility:

```java
class CardPayment extends BasePayment implements PaymentMethod {
    @Override
    public void pay(int amount) {
        // implementation
    }
}
```

These method kinds have different invocation, inheritance, and overriding rules. This chapter only establishes the taxonomy; the dedicated interface-method milestone explains `default`, `static`, and `private` in detail.

## <a id="interface-implementation">Implementing Multiple Contracts</a>

A class may implement multiple interfaces. The running example can combine a consumer-facing contract, shared implementation, and an independent capability:

```java
interface Refundable {
    void refund(int amount);
}

class CardPayment extends BasePayment
        implements PaymentMethod, Refundable {

    @Override
    public void pay(int amount) { ... }

    @Override
    public void refund(int amount) { ... }
}
```

This lets one object play several roles without multiple class inheritance.

Compatible abstract requirements from several interfaces can be satisfied by one implementation. Competing `default` methods use separate conflict-resolution rules, which are learned after interface inheritance and interface method kinds are established.

### RELATION

We now have two tools that may both participate in a contract-oriented design: abstract classes and interfaces. The next step compares them directly and asks **when should each be chosen?**
