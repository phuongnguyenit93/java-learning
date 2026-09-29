# Inheritance

Encapsulation lets each object protect its own boundary. Now we need to model cases where a concrete type can be treated as a more general type, for example when a `CardPayment` can be used wherever code only needs a `PaymentMethod`.

## <a id="is-a-subtyping">Inheritance and Subtyping</a>

### WHAT — what should “is-a” really mean?

In Java, class inheritance with `extends` creates a subtype relationship: an object of the subclass can be assigned to a superclass reference when the type system permits it.

```java
PaymentMethod payment = new CardPayment();
```

But “is-a” should not merely mean “these classes share some fields”. It should mean something stronger: **the subtype can honor the contract that callers expect from the supertype**.

If `CardPayment` only reuses code from `PaymentMethod` but breaks important expectations of the contract, the code may compile while the design is still wrong.

### RELATION — inheritance and subtyping serve different motivations

Java class inheritance can carry two ideas at once:

```text
implementation reuse
→ inherit behavior/state from a base class

subtyping
→ use a subclass object where a supertype is required
```

These motivations are often conflated. If the only goal is to reuse a few methods, composition may create less coupling. If callers truly need a subtype contract, inheritance/subtyping has a clearer design reason.

Interfaces also create subtyping without class implementation inheritance; the `abstract-interface` module goes deeper into interface and abstract-class mechanics.

### BOUNDARY — Java allows only one direct superclass

A Java class can `extend` only **one direct class**. This avoids some state/implementation conflicts of multiple class inheritance, but it also makes the superclass choice a strong coupling decision.

A `final class` cannot be subclassed. In design terms, `final` can express that the type is not intended to have its class contract extended through inheritance.

### PRACTICE — test “is-a” through behavior

Do not ask only “does the name `CardPayment` sound like a payment?”. Ask: **can every caller that knows only `PaymentMethod` use `CardPayment` without special-casing it?**

## <a id="inherited-state-behavior">Inherited State and Behavior</a>

### HOW — what does a subclass object contain?

A subclass object has the state/behavior defined by its superclass plus its own additions. Accessible instance methods may be inherited; private superclass state still exists but cannot be accessed directly through normal member access from the subclass.

### DISTINCTION — declared, inherited, and accessible are different questions

Keep these questions separate:

```text
Where is the member declared?
        ↓
Is that member inherited by the subtype?
        ↓
Does the current code have access to it?
```

Private superclass state is still part of a `Child` object, but `Child` source code cannot directly access that field. A superclass method may be inherited and usable when access rules allow it.

The complete `private` / package-private / `protected` / `public` rules belong to `class-object → Access Modifier`; this chapter keeps only the mental model needed for inheritance.

```java
class PaymentMethod {
    String provider() { return "generic"; }
}

class CardPayment extends PaymentMethod {
    String cardNetwork() { return "VISA"; }
}
```

A `CardPayment` may use `provider()` when it is accessible while also providing behavior of its own.

### Constructors are not inherited

Constructors establish state for each layer of the object. A subclass constructor invokes a superclass constructor explicitly with `super(...)` or implicitly when a valid `super()` call can be inserted.

This matters because the base-class state and invariants must be established before subclass construction is complete.

### HOW — constructor chaining initializes the superclass part first

For example:

```java
class Parent {
    Parent() {
        System.out.println("Parent");
    }
}

class Child extends Parent {
    Child() {
        System.out.println("Child");
    }
}
```

`new Child()` creates one object, but construction proceeds through a chain:

```text
new Child()
    ↓
Parent constructor
    ↓
parent state established
    ↓
Child constructor
    ↓
child state established
```

Constructors are **not inherited**; `super(...)` is how the subtype constructor asks the supertype constructor to initialize the state owned by the supertype.

### RELATION — inherited behavior is not yet the interesting part of polymorphism

Simply “having a method from a parent” is not the key mechanism. When a subclass **overrides** an instance method and a caller holds a supertype reference, runtime method selection becomes important. That is the bridge to polymorphism and dynamic dispatch.

## <a id="inheritance-coupling">Inheritance Risks</a>

### WHY — inheritance is powerful but strongly coupling

A subclass may depend not only on a public contract but also on initialization order, protected hooks, and assumptions about base-class behavior.

For example, if a base constructor calls an overridable method, subclass behavior can run before subclass state is fully initialized. That is one form of fragile-base risk.

```java
class Parent {
    Parent() {
        printLength();
    }

    void printLength() {
    }
}

class Child extends Parent {
    private String name = "Java";

    @Override
    void printLength() {
        System.out.println(name.length());
    }
}
```

During `new Child()`, the `Parent` constructor runs before `Child` field initialization is complete. The call to `printLength()` still uses dynamic dispatch and can reach `Child.printLength()` while `name` still has its default value `null`, causing `name.length()` to throw `NullPointerException`.

This shows that inheritance coupling is not limited to public signatures. A subclass can also depend on **lifecycle order and internal execution decisions of the base class**.

### TRADE-OFF — when is inheritance justified?

Inheritance is a stronger fit when:

- the subtype relationship is meaningful in behavior;
- callers genuinely need substitutability;
- the base class exposes a stable contract;
- the subclass does not have to override large parts of the base merely to escape its assumptions.

Inheritance is suspicious when the main reason is “avoid copying a few methods”. In that case composition/delegation is often more explicit and less coupled.

### RELATION — from inheritance to polymorphism

Once a `PaymentMethod` reference may point to `CardPayment`, `WalletPayment`, or another implementation, callers should not need `if (payment instanceof ...)` for every type. **Polymorphism** addresses exactly that problem.
