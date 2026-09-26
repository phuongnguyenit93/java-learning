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
final class Profile {
    private final String name;
    ...
}
```

Immutability simplifies sharing, caching, hashing, and concurrency reasoning.

## <a id="defensive-copy-input">Defensive Copy on Input</a>

If a constructor stores a mutable input reference directly, the caller can mutate internal state after construction.

```java
this.roles = new ArrayList<>(roles);
```

Copying input transfers ownership away from the caller's mutable collection.

Immutable inputs such as `String` do not need defensive copying merely for appearance.

## <a id="defensive-copy-output">Defensive Copy on Output</a>

Do not return mutable internal references when callers are not allowed to modify state.

Possible contracts include:

- immutable/unmodifiable views;
- `List.copyOf(...)`;
- fresh copies.

An unmodifiable view is not automatically deep immutability; the underlying data may still change elsewhere.

## <a id="deep-immutability">Shallow vs Deep Immutability</a>

`final List<Address> addresses` is not deeply immutable if the list or `Address` instances remain mutable.

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
