# Choosing an Abstract Class or an Interface

There is no rule that interfaces are always better than abstract classes or vice versa. The mechanisms solve overlapping but distinct design needs. The choice should begin with **the relationship being modeled**, not a syntax preference.

Return to the payment example:

```text
PaymentMethod
→ every payment implementation must provide pay(...)

BasePayment
→ some payment implementations share provider state, validation, and base behavior

Refundable
→ only payment types supporting refunds need this capability
```

Those needs should not be forced into one mechanism. A consumer-facing contract, reusable base implementation, and optional capability are different responsibilities.

## <a id="abstract-vs-interface-state">State and Constructors</a>

An abstract class may own instance state and define constructors that establish shared base state:

```java
abstract class BasePayment {
    private final String provider;

    protected BasePayment(String provider) {
        this.provider = provider;
    }
}
```

An interface has no per-instance fields or constructors of its own. Every interface field is implicitly `public static final`, so it belongs to the interface type rather than becoming per-instance object state.

If several related subtypes truly share state, invariants, and initialization rules, an abstract class is often the more natural mechanism.

## <a id="abstract-vs-interface-inheritance">One Parent Class, Many Interfaces</a>

A Java class may extend only one class:

```text
class inheritance
→ one direct superclass
```

but it may implement multiple interfaces:

```text
interface implementation
→ several independent roles/capabilities may be combined
```

That makes interfaces useful for roles that cross unrelated class hierarchies, such as `Comparable`, `AutoCloseable`, `Serializable`, or a domain capability like `Refundable`.

## <a id="selection-guidance">Selection Guidance</a>

A practical heuristic:

| Need | Often a better fit |
| --- | --- |
| shared state and initialization rules | abstract class |
| reusable base implementation tied to one class family | abstract class |
| role/capability contract | interface |
| unrelated classes sharing one contract | interface |
| one class playing several roles | interface |
| flexible contract plus partial implementation | interface + abstract base class |

The mechanisms can work together:

```java
interface PaymentMethod {
    void pay(int amount);
}

abstract class BasePayment implements PaymentMethod {
    private final String provider;

    protected BasePayment(String provider) {
        this.provider = provider;
    }
}

class CardPayment extends BasePayment implements Refundable {
    ...
}
```

The interface defines the contract consumers depend on; the abstract class supplies reusable implementation for one branch. Another implementation can still implement `PaymentMethod` without extending `BasePayment`.

Once that basic choice is clear, the next step asks how **interfaces themselves can inherit and compose contracts**.
