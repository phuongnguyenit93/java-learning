# Interface

Abstract classes work well when related subtypes share state and implementation. But many designs only need to say that a type **has a capability**, regardless of which class hierarchy it belongs to. Interfaces fit that role because the contract does not force every implementation into one class hierarchy.

## <a id="interface-contract">What Is an Interface?</a>

An `interface` names a **behavioral role or contract** that a class, enum, record, or proxy may implement.

```java
interface PaymentMethod {
    void pay(int amount);
}
```

Any type implementing `PaymentMethod` promises that callers may request `pay(...)` without knowing the concrete class.

### WHY

Interfaces reduce unnecessary dependence on concrete implementations:

```java
void checkout(PaymentMethod payment) {
    payment.pay(100);
}
```

The consumer depends on a capability rather than on `CardPayment`, `WalletPayment`, or another specific implementation.

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

`MAX_RETRY` is a constant attached to the interface type, not per-instance state.

An interface field must be initialized where it is declared. There is no instance constructor that can assign the field later:

```java
interface InvalidLimits {
    int MAX_RETRY; // compile error: variable MAX_RETRY not initialized
}
```

`final` prevents reassignment of the reference, but it does not make a referenced mutable object immutable. Avoid exposing mutable global state through interface fields.

## <a id="interface-method-kinds">Interface Method Kinds</a>

Modern Java interfaces may contain several method kinds:

| Kind | Role |
| --- | --- |
| abstract instance method | contract the implementation must satisfy |
| `default` method | inheritable default behavior |
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

These categories have different invocation, inheritance, and overriding rules. Later chapters examine them in detail.

## <a id="interface-implementation">Implementing Multiple Contracts</a>

A class may implement multiple interfaces. The running example can now combine a public contract, shared implementation, and an independent capability:

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

Compatible abstract contracts can be satisfied by one implementation. Conflicting default methods require explicit resolution; we return to that at the end of the module.

### RELATION

We now have two mechanisms that can both describe contracts: abstract classes and interfaces. The next chapter compares them directly and asks **when should each be chosen?**
