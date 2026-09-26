# null

`null` is a special reference value meaning **this reference currently identifies no object**. It is not an object, not an empty String, and not a universal primitive default.

## <a id="null-reference">null as a Reference Value</a>

Reference variables may hold `null` where the type/context allows it:

```java
String name = null;
```

Primitive variables cannot hold `null`. Wrappers can, because wrappers are reference types.

`null` is different from empty/default domain values:

```text
null        → no object is identified
""          → an existing empty String value/object
new int[0]  → an existing array object with length 0
0           → a valid primitive int value
```

Using one null state to mean "not found," "not loaded," "not applicable," and "invalid" at the same time makes an API contract hard to reason about.

## <a id="null-dereference">Dereferencing null</a>

Using `null` as though it identifies an object, such as `name.length()`, causes `NullPointerException`.

NPE often reflects an unclear boundary/invariant rather than merely the existence of `null`: the API did not state whether absence was allowed, or code violated the expected state.

Dereference can also be indirect:

```java
user.getName();
users[0].getName(); // the element itself may be null

Integer count = null;
int x = count;      // unboxing can also throw NPE
```

A null array reference and a non-null array containing null elements are different states:

```java
String[] a = null;
String[] b = new String[1]; // b exists, b[0] == null
```

## <a id="null-comparison">Comparing with null</a>

Use `==`/`!=` for null checks:

```java
if (user != null) {
    user.run();
}
```

Calling `user.equals(null)` is the wrong direction because it already dereferences `user` before `equals` executes.

Short-circuit boolean operators naturally support null guards.

`instanceof` with `null` evaluates to `false`.

When null-safe object equality is appropriate, `Objects.equals(a, b)` can express that intent clearly, but it should not replace understanding the domain's nullability contract.

## <a id="null-api-design">Nullability in API Design</a>

An API should make clear whether parameters, returns, or collection elements may be null and what null means.

Not every absence requires `Optional`, but ambiguous null contracts force callers to guess and often move failures far away from their cause.

If null violates an invariant, fail fast near the boundary:

```java
UserService(UserRepository repository) {
    this.repository = Objects.requireNonNull(repository);
}
```

Collection APIs should also define whether they return null or empty collections, whether null elements are allowed, and what a nullable argument means.

`Optional` can model absence for some return contracts, but it is not an automatic replacement for every nullable parameter/field. Consistent semantics matter more than mechanically eliminating the word `null`.

The final chapter connects the module through compile-time types, runtime types, and the boundary between static and runtime checks.
