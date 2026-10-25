# Designing with Abstract Classes and Interfaces

After this module, the first design question should not be “is an `abstract class` or an `interface` better?” A better question is: **what relationship are we trying to model?**

## <a id="abstraction-design-synthesis">End-to-End Decision Model</a>

Use this sequence of questions:

```text
Does the consumer only need a common contract/capability?
→ prefer an interface

Does a closely related class family share state + initialization rules + base implementation?
→ consider an abstract class

Does one class need several independent roles?
→ implement several interfaces

Do we need a flexible public contract while one implementation branch still shares code/state?
→ interface + abstract base class

Do interfaces need to compose smaller contracts?
→ interface inheritance

Does an interface need default instance behavior?
→ use default

Does it need an operation owned by the interface type itself?
→ use static

Does it need internal implementation reuse?
→ use private / private static

Do inherited defaults compete?
→ apply specificity, class-hierarchy precedence, and explicit override rules
```

The mechanism should follow from the **modeling need**, not from syntax familiarity.

## <a id="abstraction-design-example">Putting the Payment Example Together</a>

```text
PaymentMethod
→ public contract used by checkout(...)

BasePayment
→ abstract base for one implementation branch sharing provider, validation, and base behavior

CardPayment
→ concrete class completing payment behavior

Refundable
→ independent capability that CardPayment or unrelated types may implement
```

A combined example:

```java
interface PaymentMethod {
    void pay(int amount);

    default String label() {
        return "payment";
    }
}

interface Refundable {
    void refund(int amount);
}

abstract class BasePayment implements PaymentMethod {
    private final String provider;

    protected BasePayment(String provider) {
        this.provider = provider;
    }

    protected String provider() {
        return provider;
    }
}

class CardPayment extends BasePayment implements Refundable {
    CardPayment() {
        super("card");
    }

    @Override
    public void pay(int amount) { ... }

    @Override
    public void refund(int amount) { ... }
}
```

No mechanism in the example replaces the others. Each mechanism owns a different responsibility.

## <a id="abstraction-design-checklist">Design Checklist</a>

Before choosing an abstract class or interface, ask:

1. Does the caller need a **contract** or does it truly need one **concrete implementation**?
2. Do the related subtypes genuinely share state, invariants, or initialization rules?
3. Is this a natural class-family relationship or a role/capability that crosses class families?
4. Does one class need several independent roles?
5. If an interface uses `default`, is that default behavior actually valid for every legitimate implementation?
6. When several interfaces are combined, can method signatures or default behavior conflict?
7. If `InterfaceName.super` is needed, is the named interface an appropriate direct superinterface under Java syntax rules?

This helps avoid two common design mistakes: choosing an abstract class merely to reuse a few lines of code, or creating an interface simply to add another layer of indirection without a clear contract.

## <a id="abstraction-final-mental-model">Final Mental Model</a>

```text
abstract class
→ closely related class family
→ state + initialization + partial implementation
→ only one superclass

interface
→ contract / role / capability
→ several interfaces on one class
→ may form contract hierarchies
→ default/static/private methods have distinct roles

when combined
→ interface defines what consumers may rely on
→ abstract class may provide shared implementation for one branch
→ concrete class completes the behavior
→ conflict rules keep inherited dispatch unambiguous
```

These mechanisms support polymorphism, but they are not the whole of OOP. OOP is the broader context of object modeling, encapsulation, inheritance, polymorphism, and relationships among types.
