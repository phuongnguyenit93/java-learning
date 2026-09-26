# null

`null` is a special reference value meaning **this reference currently identifies no object**. It is not an object, not an empty String, and not a universal primitive default.

## <a id="null-reference">null as a Reference Value</a>

Reference variables may hold `null` where the type/context allows it:

```java
String name = null;
```

Primitive variables cannot hold `null`. Wrappers can, because wrappers are reference types.

## <a id="null-dereference">Dereferencing null</a>

Using `null` as though it identifies an object, such as `name.length()`, causes `NullPointerException`.

NPE often reflects an unclear boundary/invariant rather than merely the existence of `null`: the API did not state whether absence was allowed, or code violated the expected state.

## <a id="null-comparison">Comparing with null</a>

Use `==`/`!=` for null checks:

```java
if (user != null) {
    user.run();
}
```

Calling `user.equals(null)` is the wrong direction because it already dereferences `user` before `equals` executes.

Short-circuit boolean operators naturally support null guards.

## <a id="null-api-design">Nullability in API Design</a>

An API should make clear whether parameters, returns, or collection elements may be null and what null means.

Not every absence requires `Optional`, but ambiguous null contracts force callers to guess and often move failures far away from their cause.

The final chapter connects the module through compile-time types, runtime types, and the boundary between static and runtime checks.
