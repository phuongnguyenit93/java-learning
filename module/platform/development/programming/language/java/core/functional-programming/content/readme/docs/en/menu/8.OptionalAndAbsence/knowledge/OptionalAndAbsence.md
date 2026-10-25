# Optional and Explicit Absence

## <a id="optional-purpose">Why Optional Exists</a>

APIs often need to say "there may be no value". Returning `null` can express that, but the absence is easy to overlook because the type itself does not remind the caller to handle it.

`Optional<T>` makes possible absence explicit in the return type. A useful mental model is a container with either one non-null value or no value.

```java
Optional<User> findUser(String id) {
    // returns Optional.empty() when the user is not found
}
```

`Optional` does not eliminate `null` from Java and is not a universal wrapper for every reference. Its value is strongest at selected API boundaries where absence is a normal, expected outcome.

## <a id="optional-creation">Creating Optional Values</a>

Use the creation method that matches what you know about the value.

```java
Optional<String> none = Optional.empty();
Optional<String> known = Optional.of("Java");
Optional<String> maybe = Optional.ofNullable(valueFromLegacyApi);
```

`Optional.of(value)` requires `value` to be non-null and throws `NullPointerException` otherwise. `Optional.ofNullable(value)` converts `null` to `Optional.empty()`.

Choose deliberately. Using `ofNullable` everywhere can hide a bug when the value was supposed to be non-null.

For primitive-heavy APIs, the JDK also provides `OptionalInt`, `OptionalLong`, and `OptionalDouble`. They express optional primitive results without wrapping the payload in `Integer`, `Long`, or `Double`. They are separate types with smaller APIs, not subclasses of `Optional<T>`.

## <a id="optional-null-boundary">Optional and the null Boundary</a>

An `Optional` should itself normally be non-null. Returning `null` instead of `Optional.empty()` defeats the contract because callers now have two different forms of absence to handle.

`Optional` also defines how `null` interacts with transformations:

- `map` turns a `null` mapper result into `Optional.empty()`;
- `flatMap` expects the mapper itself to return a non-null `Optional` and throws `NullPointerException` if it returns `null`.

```java
Optional<String> name = Optional.of("Java");
Optional<Integer> length = name.map(String::length);
```

At boundaries with older APIs that may return `null`, `ofNullable` is the usual bridge into an `Optional`-based flow.

## <a id="optional-presence">Presence and Absence</a>

`isPresent()` and `isEmpty()` let you inspect whether a value exists.

`isPresent()` has existed since Java 8; `isEmpty()` was added in Java 11. Code that must compile against Java 8 cannot use `isEmpty()`.

```java
if (user.isPresent()) {
    System.out.println(user.get().name());
}
```

That code is legal, but repeatedly checking then calling `get()` often recreates the same branching style that `Optional` was meant to make explicit. When the operation fits, methods such as `map`, `filter`, `ifPresent`, or fallback methods keep the "present vs absent" decision attached to the `Optional`.

Direct presence checks are still reasonable when the surrounding control flow is genuinely clearer that way. `Optional` is an API tool, not a rule that forbids ordinary `if` statements.

## <a id="optional-transform-filter">Transforming and Filtering Optional Values</a>

`map` transforms a present value. If the `Optional` is empty, the mapper is not called and the result stays empty.

```java
Optional<String> normalizedName = user
        .map(User::name)
        .map(String::trim);
```

If a mapping function already returns `Optional`, use `flatMap` to avoid producing `Optional<Optional<T>>`.

```java
Optional<Address> address = user.flatMap(User::address);
```

`filter` keeps a present value only when its predicate returns `true`; otherwise it produces an empty `Optional`.

```java
Optional<User> active = user.filter(User::isActive);
```

These operations are useful because absence automatically skips the transformation or predicate without an explicit null check at each step.

## <a id="optional-consumption">Consuming a Present Value</a>

When you need to perform an action only if a value exists, `ifPresent` expresses that directly.

```java
user.ifPresent(found -> audit.log("Found " + found.id()));
```

When both cases need actions, `ifPresentOrElse` keeps the two branches together.

```java
user.ifPresentOrElse(
        this::displayUser,
        () -> displayMessage("User not found")
);
```

These methods are appropriate for terminal actions. If you still need a value for further computation, prefer transformation or fallback methods rather than using mutable holders inside `ifPresent`.

`ifPresentOrElse` was added after the original Java 8 `Optional` API, in Java 9. This Java Core module teaches the current stable API; the Java 8 feature module remains the place for release-specific introduction context.

## <a id="optional-fallback-and-failure">Fallback and Failure Strategies</a>

Choose a fallback based on whether it is cheap, expensive, optional-producing, or exceptional.

```java
String a = userName.orElse("anonymous");
String b = userName.orElseGet(this::loadDefaultName);
Optional<String> c = userName.or(this::lookupSecondaryName);
String d = userName.orElseThrow(UserNotFoundException::new);
```

`orElse(value)` evaluates its argument before the call, even when the `Optional` is present. `orElseGet(supplier)` calls the supplier only when the value is absent. That difference matters for expensive work or side effects.

`or` is the lazy alternative when the fallback itself is another `Optional`. `orElseThrow` makes absence a failure and can supply an exception appropriate to the API boundary.

`Optional.or` was also added in Java 9. If a project must retain Java 8 source compatibility, do not assume that every method in the current `Optional` API exists in that release.

## <a id="optional-api-design">Optional at API Boundaries</a>

`Optional` is most useful as a return type when "no result" is a normal possibility callers should see in the method contract.

```java
Optional<Order> findOrder(OrderId id);
```

It is usually less helpful as a field or parameter used everywhere. A parameter often has a clearer domain model if separate methods, overloads, a request object, or a nullable convention already expresses the choice. A collection-returning method often communicates "no elements" naturally with an empty collection instead of `Optional<List<T>>`.

These are design guidelines, not compiler restrictions. The question is whether `Optional` makes the API's absence semantics clearer for its callers.

## <a id="optional-misuse">Common Optional Misuses</a>

Common problems come from treating `Optional` as ceremony rather than an absence model.

- Returning `null` from a method declared as `Optional<T>` creates two absence representations.
- Calling `get()` without proving presence can throw `NoSuchElementException` and usually hides the intended fallback or failure rule.
- Wrapping a value in `Optional` only to immediately unwrap it adds noise without improving the contract.
- Using `Optional` for every field, parameter, and collection can spread wrapper handling through code without clarifying domain semantics.
- Putting side effects inside `map` makes a transformation unexpectedly stateful; use a terminal action or explicit boundary when an effect is the real goal.

Use `Optional` where the type should make possible absence explicit in the API contract. It makes absence visible to callers, but it does not force a particular handling strategy. Elsewhere, choose the representation that communicates the domain most directly.
