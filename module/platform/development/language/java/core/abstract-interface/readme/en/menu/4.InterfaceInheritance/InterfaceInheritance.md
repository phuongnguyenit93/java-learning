# Interface Inheritance

An interface may represent one small capability. Larger APIs can compose those capabilities into broader contracts through interface inheritance.

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

This is multiple inheritance of **types/contracts**, not multiple copies of object state.

Compatible abstract signatures compose naturally. Incompatible return types or competing default methods can make the hierarchy invalid or require explicit conflict resolution.

## <a id="interface-redeclaration">Redeclaring Inherited Methods</a>

A subinterface may redeclare an inherited method to:

- add documentation or annotations;
- narrow a return type covariantly;
- turn an inherited default method back into an abstract requirement.

```java
interface Base {
    default Number value() { return 0; }
}

interface Specific extends Base {
    @Override
    Integer value();
}
```

Redeclaration should clarify or refine the contract rather than merely duplicate a signature.

Next we look at why interfaces have `default`, `static`, and `private` methods and what each category is for.
