# Default, Static and Private Interface Methods

Early Java interfaces were mostly thought of as collections of abstract methods. Modern interfaces support additional method kinds to help with **API evolution** and reuse behavior without turning the interface into an abstract class.

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

## <a id="static-interface-method">Static Interface Methods</a>

A static interface method belongs to the interface type itself:

```java
PaymentMethod.validate(amount);
```

It is **not inherited as an instance method** by implementing classes.

Static interface methods work well for factories, validators, or helpers closely tied to the contract but independent of instance state.

## <a id="private-interface-method">Private Interface Helpers</a>

Private interface methods let default/static methods share implementation details without expanding the public contract.

```java
interface Formatter {
    default String upper(String value) {
        return normalize(value).toUpperCase();
    }

    private String normalize(String value) {
        return value.trim();
    }
}
```

Implementing classes cannot call or override `normalize(...)`; it is purely an internal helper.

## <a id="default-method-evolution">Interface Evolution</a>

One major reason default methods exist is **interface evolution**: a library can add behavior with a default implementation without forcing every old implementation to add source code immediately.

Default methods do not make every change compatible, however:

- adding an abstract method can still break source compatibility;
- adding a default may create a conflict with another interface;
- changing the behavioral contract may still break clients.

The final chapter uses default methods to show how Java resolves multiple-interface conflicts.
