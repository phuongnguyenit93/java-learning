# Constructor

A constructor is more than syntax executed after `new`. It is where an object transitions from “being created” to **having valid initial state**.

## <a id="constructor-purpose">Constructor Purpose</a>

A constructor should establish invariants required for the object to be usable.

```java
class Account {
    private final String id;
    private int balance;

    Account(String id, int openingBalance) {
        if (openingBalance < 0) throw new IllegalArgumentException();
        this.id = id;
        this.balance = openingBalance;
    }
}
```

Callers should not normally receive a half-valid object that requires a mandatory sequence of setter calls before use.

## <a id="constructor-overloading">Constructor Overloading and Chaining</a>

A class may declare multiple constructors with different parameter lists.

```java
Account(String id) {
    this(id, 0);
}
```

`this(...)` delegates to another constructor in the same class so validation/initialization logic can stay in one place.

## <a id="default-constructor">Default Constructor</a>

The compiler provides a no-argument default constructor only when the class declares **no constructor at all**.

Once you declare:

```java
Account(String id) { ... }
```

the compiler no longer adds `Account()` automatically.

This often matters for frameworks or code expecting a no-arg constructor.

## <a id="constructor-exceptions">Constructor Failure</a>

A constructor may throw if it cannot establish valid state.

If construction fails, the `new` expression does not return a fully constructed reference to the caller. However, side effects performed before failure can still have happened.

Avoid publishing `this` from a constructor too early; the Object Creation Lifecycle chapter returns to that risk.

Next we examine the two special references used during instance construction/member access: `this` and `super`.
