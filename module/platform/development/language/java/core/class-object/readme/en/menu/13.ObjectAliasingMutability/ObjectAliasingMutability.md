# Aliasing and Mutability

Aliasing occurs when multiple references point to the same object. With immutable values this is usually safe. With mutable objects, a change through one alias may appear unexpectedly elsewhere.

## <a id="aliasing-model">Multiple References, One Object</a>

```java
List<String> a = new ArrayList<>();
List<String> b = a;

b.add("x");
System.out.println(a); // [x]
```

Assignment did not copy the collection; it copied the reference.

This connects directly to Java pass-by-value: a method receives a copy of the reference value and can therefore mutate the same object.

## <a id="shared-mutable-state">Shared Mutable State</a>

Shared mutable state complicates reasoning because several locations may change the same object.

Typical consequences include:

- invariants broken outside the owner;
- order-dependent tests;
- concurrency races;
- caches/views changing indirectly;
- unclear mutation responsibility.

Mutability is not inherently wrong; unclear ownership is the bigger problem.

## <a id="aliasing-in-collections">Collection Aliasing</a>

A getter returning an internal mutable collection leaks the reference:

```java
List<String> getRoles() {
    return roles;
}
```

Callers can mutate internal state without going through the owner's rules.

The same problem occurs when a constructor stores a caller-owned mutable input directly.

The final chapter addresses this with immutability and defensive copying.
