# Multiple Inheritance Through Interfaces

Java does not allow a class to inherit implementation from several parent classes, but a class may implement several interfaces. Pure abstract contracts usually compose cleanly. The interesting conflicts appear when multiple interfaces provide **default implementations** for the same method.

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

a class implementing both must override `name()` and resolve the ambiguity itself.

Java does not guess which behavior is intended when neither interface is more specific.

## <a id="class-wins-rule">Class Methods Win</a>

If the class hierarchy already provides a compatible concrete instance method, that class method takes precedence over an interface default.

```text
concrete class method
→ wins

interface default
→ fallback when the class hierarchy supplies no implementation
```

Interface defaults therefore do not silently replace behavior inherited from classes.

## <a id="explicit-super-interface">InterfaceName.super</a>

When a class overrides a conflicting default, it may explicitly invoke a directly inherited interface default:

```java
@Override
public String name() {
    return A.super.name() + B.super.name();
}
```

`InterfaceName.super.method()` lets a class combine default behavior instead of reimplementing everything.

## <a id="diamond-interface">Diamond-Shaped Interface Inheritance</a>

A diamond shape is not automatically ambiguous.

If both paths ultimately inherit the same **most-specific** default method, the contract remains unambiguous. A conflict appears only when independent defaults compete and neither is more specific.

The key mental model is:

```text
multiple interface inheritance
→ combines types/contracts and possibly default behavior
→ does not duplicate instance state like multiple class inheritance would
```

After this module, the first design question should be **what relationship are we modeling?** Shared state/partial implementation points toward an abstract class; a flexible capability contract points toward an interface.
