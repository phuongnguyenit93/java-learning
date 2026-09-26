# Default, Static and Private Interface Methods

Early Java interfaces were mostly thought of as collections of abstract methods. Modern interfaces support additional method kinds to help with **API evolution** and reuse behavior without turning the interface into an abstract class.

The three method families in this chapter solve different problems:

```text
default
→ provide inheritable instance behavior

static
→ place an operation attached to the contract on the interface type itself

private / private static
→ reuse internal logic without expanding the public contract
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

Existing implementations may inherit `label()` without immediately providing a new body.

Default methods remain virtual instance behavior and may be overridden. When several defaults compete, Java applies explicit resolution rules rather than guessing.

### WHY

If a public interface already has many implementations, adding a new abstract method forces those implementations to provide it. A `default` method can supply common behavior at the contract when a meaningful default truly exists.

## <a id="static-interface-method">Static Interface Methods</a>

A static interface method belongs to the interface type itself. It is useful when an operation belongs closely to the contract but needs no particular object state:

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

It is **not inherited as an instance method** by implementing classes.

Static interface methods work well for factories, validators, or helpers closely tied to the contract but independent of instance state. Keeping such an operation on the interface can keep the API near the contract instead of scattering it into an unrelated utility class.

## <a id="private-interface-method">Private Interface Helpers</a>

Private interface methods let methods inside the interface share implementation details without expanding the public contract.

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

`private String normalize(...)` is a **private instance method**, so it belongs to instance/default context. `private static String normalizeStatic(...)` is a **private static method**, so it can be used from static context without an instance.

Implementing classes cannot call or override these private helpers; they are internal implementation details.

## <a id="default-method-evolution">Interface Evolution</a>

One major reason default methods exist is **interface evolution**: a library can add behavior with a default implementation without forcing every old implementation to add source code immediately.

Default methods do not make every change compatible, however:

- adding an abstract method can still break source compatibility;
- adding a default may create a conflict with another interface;
- changing the behavioral contract may still break clients.

The final chapter uses default methods to show how Java resolves multiple-interface conflicts.
