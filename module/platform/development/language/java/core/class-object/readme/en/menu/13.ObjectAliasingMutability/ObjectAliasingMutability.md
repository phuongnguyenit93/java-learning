# Object Aliasing and Mutability

## <a id="aliasing-model">Multiple references to one mutable object</a>
Aliasing exists when multiple references identify the same object. With immutable objects this is usually harmless; with mutable objects, a mutation through one alias becomes visible through the others. Reason about identity and ownership, not just variable names.

```java
List<String> a = new ArrayList<>();
List<String> b = a;
b.add("x"); // a now also observes "x"
```

## <a id="shared-mutable-state">Shared mutable state consequences</a>
Shared mutable state increases coupling because a caller can observe changes caused elsewhere. It complicates invariants, testing, caching, concurrency, and reasoning about who may modify data. Encapsulation, immutability, ownership rules, and copies reduce this uncertainty.

## <a id="aliasing-in-collections">Aliasing through collections and returned references</a>
Returning an internal mutable collection, storing a caller-provided mutable object directly, or exposing arrays can leak aliases across an API boundary. An unmodifiable wrapper prevents mutation through that wrapper but may still reflect source mutation; a defensive copy changes ownership semantics.
