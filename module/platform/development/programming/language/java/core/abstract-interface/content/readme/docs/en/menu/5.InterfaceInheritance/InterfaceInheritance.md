# Interface Inheritance and Contract Composition

An interface may represent one small capability. Larger APIs can compose those capabilities into broader contracts through **interface inheritance**.

Putting every behavior into one large interface can force implementers to depend on capabilities they do not need. Smaller roles keep contracts focused:

```text
Payable
Refundable
Auditable
```

A broader contract can then compose only the roles it actually needs. Interface inheritance is therefore more than `extends` syntax: it is a way to **compose contracts** while preserving clear capability boundaries.

## <a id="interface-extends-interface">Interface Inheritance</a>

An interface uses `extends` to inherit one or more interfaces:

```java
interface Payable {
    void pay();
}

interface RefundablePayment extends Payable {
    void refund();
}
```

A class implementing `RefundablePayment` must satisfy both `pay()` and `refund()`.

A subinterface may also add methods or refine a compatible return type.

## <a id="multiple-interface-hierarchy">Multiple Parent Interfaces</a>

An interface may extend multiple interfaces:

```java
interface AuditedPayment extends Payable, Auditable { ... }
```

This is multiple inheritance of **types/contracts**. Interfaces contribute no per-instance fields or object state, and this is not multiple class inheritance.

Compatible abstract signatures compose naturally. If return types conflict and cannot be satisfied simultaneously, the child interface can be invalid at compile time.

Conflicts involving `default` methods are deferred to the later milestones after the role of `default` has been established.

## <a id="interface-redeclaration">Redeclaring Inherited Methods</a>

A subinterface may redeclare an inherited method to:

- add documentation or annotations;
- narrow a return type covariantly;
- clarify the contract exposed by the child interface.

```java
interface Base {
    Number value();
}

interface Specific extends Base {
    @Override
    Integer value();
}
```

`Integer` is a narrower return type that remains compatible with `Number`, so the child interface can refine the contract this way.

Redeclaration should clarify or refine the contract rather than merely repeat the same signature.

Next we examine why modern interfaces support `default`, `static`, and `private` methods and what problem each kind solves.
