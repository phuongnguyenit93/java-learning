# Multiple Interface Inheritance and Conflict Resolution

Java does not let one class extend several parent classes, but a class may implement several interfaces. Pure abstract contracts usually compose when their signatures are compatible. The interesting conflicts appear when multiple interfaces provide competing **default methods** for the same signature.

Read default-method resolution as an ordered process:

```text
Is there an override-equivalent class-side member?
→ static method: it cannot implement the interface instance method; the class is invalid if the conflict cannot be removed
→ instance method declared or inherited by the class:
   → concrete and compatible: use the class-side implementation
   → abstract: the interface default does not satisfy it automatically; the concrete subclass must implement it
   → concrete but incompatible with the interface contract: the class must provide a compatible override when possible; otherwise compilation fails

If there is no relevant class-side member above, is one interface default more specific than the others?
→ yes: use the more-specific default

Do independent competing defaults still remain?
→ yes: the implementing class must override and resolve the conflict explicitly
```

## <a id="default-method-conflict">Default Method Conflicts</a>

If two unrelated interfaces provide the same default signature:

```java
interface A {
    default String name() { return "A"; }
}

interface B {
    default String name() { return "B"; }
}
```

If no compatible concrete class-side method has already resolved this signature, a class implementing both must override `name()` and choose or combine behavior explicitly.

Java does not guess which default is intended when neither interface is more specific.

By contrast, when a child interface overrides a parent default, the child declaration is more specific:

```java
interface Parent {
    default String name() { return "parent"; }
}

interface Child extends Parent {
    @Override
    default String name() { return "child"; }
}
```

A class implementing `Child` does not choose again between `Parent.name()` and `Child.name()`; `Child` already supplies the more-specific behavior.

A conflict can also arise **inside the interface hierarchy itself**, before any class implements it. If a child interface inherits override-equivalent default methods from unrelated parent interfaces and neither parent is more specific, the child interface must declare an overriding method to resolve the conflict; otherwise the child-interface declaration is a compile-time error:

```java
interface Left {
    default String name() { return "left"; }
}

interface Right {
    default String name() { return "right"; }
}

interface Combined extends Left, Right {
    @Override
    default String name() {
        return Left.super.name();
    }
}
```

Resolving the conflict in the child interface makes the contract unambiguous before any class implements `Combined`.

## <a id="class-wins-rule">Class-Hierarchy Methods Take Precedence</a>

If the class hierarchy already provides a compatible **concrete instance method**, that implementation takes precedence over an interface default:

```java
class Named {
    public String name() {
        return "class";
    }
}

interface NamedContract {
    default String name() {
        return "interface";
    }
}

class CardPayment extends Named implements NamedContract {
}
```

For `new CardPayment().name()`, the implementation from `Named` is used. Interface defaults therefore do not silently replace concrete behavior inherited from the class hierarchy.

One important distinction: if a superclass only declares an abstract method with the same signature, the concrete subclass still has to provide a valid implementation. “Class wins” should not be understood as a runtime choice between two bodies that are always both available.

A class-side `static` method also cannot step aside and let an interface default become the instance implementation. If its signature conflicts with the interface contract, Java reports a compile-time error instead of selecting the default.

## <a id="explicit-super-interface">Calling InterfaceName.super</a>

When a class overrides a conflict, it may explicitly invoke a default method from an appropriate **direct superinterface**:

```java
@Override
public String name() {
    return A.super.name() + B.super.name();
}
```

`InterfaceName.super.method()` lets the class combine default behavior instead of reimplementing everything.

## <a id="diamond-interface">Diamond-Shaped Interface Inheritance</a>

A diamond shape is not automatically ambiguous.

If several inheritance paths ultimately lead to one **most-specific** default method, the contract remains unambiguous. A conflict appears only when independent defaults compete and no candidate is more specific.

The milestone mental model is:

```text
multiple interface inheritance
→ combines types/contracts
→ may also inherit default behavior
→ interfaces contribute no per-instance state; this is not multiple class inheritance
→ competing defaults are resolved by specificity + class-hierarchy precedence + explicit override
```

With conflict rules established, the final milestone connects the whole module into one design model: which mechanism to choose, how to combine them, and what to inspect once interface hierarchies and default behavior enter the design.
