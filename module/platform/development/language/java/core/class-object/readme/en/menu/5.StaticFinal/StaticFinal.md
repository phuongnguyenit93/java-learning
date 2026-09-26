# static and final

`static` and `final` answer different questions. `static` says **whether a member belongs to the class or each instance**; `final` restricts reassignment/overriding depending on context.

## <a id="static-vs-instance">Static vs Instance Members</a>

Instance fields/methods belong to individual objects:

```java
account.balance
account.withdraw(...)
```

Static members belong to class-level context:

```java
Account.MAX_LIMIT
Account.createDefault()
```

Static methods have no implicit `this`.

## <a id="final-variable-reference">final: Primitive vs Reference</a>

A `final` variable can be assigned only once after initialization.

```java
final int x = 10;
final List<String> names = new ArrayList<>();
```

For a reference, `final` prevents `names` from pointing to a different list, but it does **not** make the list immutable.

## <a id="static-initialization">Static Initialization</a>

Static field initializers and static initializer blocks run during class initialization according to source/superclass ordering rules.

Static mutable state is shared across all instances, so it introduces more global coupling and concurrency concerns than ordinary instance state.

## <a id="constants-design">Constants and Compile-time Constants</a>

Not every `static final` value is a compile-time constant. Java has specific rules for primitive/String constant expressions.

That distinction affects inlining and binary behavior when libraries change constants without client recompilation.

`UPPER_SNAKE_CASE` is a naming convention; the semantic contract matters more than the style.

The next chapter looks at initialization blocks outside constructor bodies.
