# static and final

`static` and `final` answer different questions. `static` says **whether a member belongs to the class or each instance**; `final` restricts reassignment/overriding depending on context.

## <a id="static-vs-instance">Static vs Instance Members</a>

Instance fields/methods belong to individual objects:

```java
bankAccount.balance
bankAccount.withdraw(...)
```

Static members belong to class-level context:

```java
BankAccount.MAX_LIMIT
BankAccount.createDefault()
```

Static methods have no implicit `this`.

## <a id="final-variable-reference">final Semantics</a>

A `final` variable may receive a value only once under Java's definite-assignment rules. It can be initialized at the declaration or assigned later exactly once along every valid initialization path.

```java
final int x = 10;
final List<String> names = new ArrayList<>();
```

For a reference, `final` prevents `names` from pointing to a different list, but it does **not** make the list immutable.

The keyword also has declaration-specific meanings that later connect to inheritance:

```text
final variable
→ reference/value cannot be reassigned after its one assignment

final method
→ subclasses cannot override that method

final class
→ cannot be subclassed
```

This chapter only needs that boundary; overriding and inheritance design belong to the OOP module.

## <a id="static-initialization">Static Initialization</a>

Static field initializers and static initializer blocks run during class initialization according to source/superclass ordering rules.

Static mutable state is shared across all instances, so it introduces more global coupling and concurrency concerns than ordinary instance state.

## <a id="constants-design">Constants and Compile-time Constants</a>

Not every `static final` value is a compile-time constant. Java has specific rules for primitive/String constant expressions.

```java
static final int MAX_DAILY_WITHDRAWALS = 3;          // compile-time constant
static final int CONFIGURED_LIMIT = Integer.parseInt("3"); // not a compile-time constant
```

The first value is a primitive initialized from a constant expression, so client bytecode may inline it. The second requires a method call and is initialized at runtime.

That distinction affects inlining and binary behavior when libraries change constants without client recompilation.

`UPPER_SNAKE_CASE` is a naming convention; the semantic contract matters more than the style.

The next chapter looks at initialization blocks outside constructor bodies.
