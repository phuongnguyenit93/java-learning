# Abstract Class vs Interface

There is no rule that interfaces are always better than abstract classes or vice versa. They solve overlapping but distinct design needs. The right choice starts with the **relationship being modeled**, not a syntax preference.

## <a id="abstract-vs-interface-state">State and Constructors</a>

An abstract class may own instance state and constructors:

```java
abstract class PaymentBase {
    private final String provider;

    protected PaymentBase(String provider) {
        this.provider = provider;
    }
}
```

An interface has no per-instance fields or constructors of its own. Interface fields are constants.

If several subtypes truly share state, invariants, and initialization rules, an abstract class may be the more natural mechanism.

## <a id="abstract-vs-interface-inheritance">One Parent Class, Many Interfaces</a>

A Java class may extend only one class but implement many interfaces.

That makes interfaces useful for roles that cut across unrelated class hierarchies, such as `Comparable`, `AutoCloseable`, or a domain capability like `Refundable`.

The distinction is important:

```text
class inheritance
→ one superclass commitment

interface implementation
→ several independent roles may be combined
```

## <a id="selection-guidance">Choosing Between Them</a>

A practical heuristic:

| Need | Often a better fit |
| --- | --- |
| shared state and constructors | abstract class |
| common base implementation tied to one hierarchy | abstract class |
| capability/role contract | interface |
| unrelated classes sharing the same role | interface |
| one class playing multiple roles | interface |
| flexible contract plus partial implementation | interface + abstract base class |

The mechanisms can work together:

```java
interface PaymentMethod { ... }
abstract class BasePayment implements PaymentMethod { ... }
```

The interface defines the contract consumers depend on; the abstract class supplies reusable implementation for one branch of implementations.

The next chapter asks how interfaces themselves can form larger contract hierarchies.
