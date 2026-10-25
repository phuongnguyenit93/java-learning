# Default, Static, and Private Interface Methods

Early Java interfaces were mostly treated as collections of abstract methods. Modern interfaces can also contain several kinds of implemented methods, but **each kind serves a different role**.

```text
default
→ inheritable default instance behavior
→ supports compatible contract evolution when a meaningful default exists

static
→ operation owned by the interface type itself
→ does not become an inherited instance method on implementing classes

private / private static
→ internal helper behavior reused by methods inside the interface
→ does not expand the public contract
```

## <a id="default-method">Default Methods</a>

A `default` method is an instance method with a body declared in the interface:

```java
interface PaymentMethod {
    void pay(int amount);

    default String label() {
        return "payment";
    }
}
```

Implementing classes may inherit `label()` without immediately writing another body.

Default methods remain virtual instance behavior and may be overridden. When several defaults compete, Java uses explicit conflict-resolution rules rather than choosing arbitrarily.

### WHY

If a public interface already has many implementations, adding a new abstract method forces those implementations to provide it. When a meaningful common default exists, a `default` method can extend the interface without forcing every existing implementation to add source code immediately.

`default` does not turn an interface into an abstract class: interfaces still have no independent instance fields or per-object constructors.

## <a id="static-interface-method">Static Interface Methods</a>

A static interface method belongs to the interface type itself. It is useful when an operation belongs closely to the contract but does not need state from a particular object:

```java
interface PaymentMethod {
    void pay(int amount);

    static void validate(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}

PaymentMethod.validate(100);
```

It is **not inherited** by implementing classes or subinterfaces. Call it through the interface that declares it, such as `PaymentMethod.validate(...)`; do not treat it as an instance method or as a member inherited under a subinterface name.

Static interface methods can work well for factories, validators, or helpers closely tied to the contract and independent of instance state. The relationship should still be meaningful; an interface should not become a miscellaneous utility container.

## <a id="private-interface-method">Private Interface Helpers</a>

Private interface methods let methods inside the interface reuse implementation details without exposing those helpers as part of the public contract:

```java
interface Formatter {
    default String upper(String value) {
        return normalize(value).toUpperCase();
    }

    private String normalize(String value) {
        return value.trim();
    }

    static String normalizeKey(String value) {
        return normalizeStatic(value).toLowerCase();
    }

    private static String normalizeStatic(String value) {
        return value.trim();
    }
}
```

`private String normalize(...)` is a private instance method and can be used from instance/default context. `private static String normalizeStatic(...)` is a private static method and can be used from static context without an instance.

Implementing classes cannot call or override these private helpers; they are internal implementation details of the interface.

## <a id="default-method-evolution">Interface Evolution</a>

One important reason default methods exist is **interface evolution**: a library may add behavior with a default implementation without forcing every old implementation to add source code immediately.

Default methods do not make every change compatible:

- adding an abstract method can still break existing implementations at compile time;
- adding a default method can create a conflict with another interface;
- changing the behavioral contract can still break client assumptions.

A child interface can also **turn an inherited default method back into an abstract requirement** by redeclaring it without a body:

```java
interface Base {
    default Number value() { return 0; }
}

interface Specific extends Base {
    @Override
    Integer value();
}
```

This is first a property of `default` behavior, and it also shows that a child interface may refine its contract instead of retaining the parent default.

The next milestone uses default methods to examine how Java resolves conflicts when one class implements several interfaces.
