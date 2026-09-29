# Immutability and Defensive Copy

When observable state cannot change after construction, sharing becomes much easier to reason about. But `final` fields alone are not enough; the entire reachable object graph and exposed references matter.

## <a id="immutable-object-design">Immutable Object Design</a>

An immutable object usually:

- establishes all state in a constructor/factory;
- exposes no mutator that changes state;
- uses `final` fields when appropriate;
- prevents mutable internal state from leaking;
- restricts extension when subclasses could violate immutability.

```java
final class BankAccountSnapshot {
    private final String id;
    private final List<String> tags;

    BankAccountSnapshot(String id, List<String> tags) {
        this.id = id;
        this.tags = new ArrayList<>(tags);
    }

    List<String> tags() {
        return List.copyOf(tags);
    }
}
```

This immutable variant of the running account example establishes all state during construction and never exposes its mutable internal list directly.

Immutability simplifies sharing, caching, hashing, and concurrency reasoning.

## <a id="defensive-copy-input">Defensive Copy on Input</a>

If a constructor stores a mutable input reference directly, the caller can mutate internal state after construction.

```java
this.tags = new ArrayList<>(tags);
```

Copying input separates the snapshot's ownership from the caller's mutable collection:

```java
List<String> source = new ArrayList<>();
BankAccountSnapshot snapshot = new BankAccountSnapshot("A-01", source);

source.add("VIP");
System.out.println(snapshot.tags()); // []
```

Immutable inputs such as `String` do not need defensive copying merely for appearance.

## <a id="defensive-copy-output">Defensive Copy on Output</a>

Do not return mutable internal references when callers are not allowed to modify state.

Possible contracts include:

- immutable/unmodifiable views;
- `List.copyOf(...)`;
- fresh copies.

For the `BankAccountSnapshot` above, `List.copyOf(tags)` returns an unmodifiable result rather than the mutable internal list itself:

```java
snapshot.tags().add("VIP"); // UnsupportedOperationException
```

An unmodifiable view is not automatically deep immutability; the underlying data may still change elsewhere.

## <a id="deep-immutability">Shallow vs Deep Immutability</a>

`final List<Address> addresses` is not deeply immutable if the list or `Address` instances remain mutable. `final` fixes the field reference; it does not recursively freeze the referenced object graph.

```text
shallow immutability
→ outer references/state cannot be reassigned

deep immutability
→ the relevant reachable object graph has immutable contracts
```

Not every domain needs deep immutability, but ownership and mutation boundaries should be explicit.

The module's final mental model is:

```text
class defines state/behavior
→ constructor establishes valid state
→ access modifiers define boundaries
→ initialization order controls when state becomes ready
→ reference copying creates aliasing
→ mutability requires clear ownership
→ immutability/defensive copying makes sharing safer
```
