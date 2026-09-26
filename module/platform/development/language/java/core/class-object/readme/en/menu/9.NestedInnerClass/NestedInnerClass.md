# Nested and Inner Classes

Placing one type inside another can express close conceptual organization or access to surrounding context. Different nested-class forms have very different semantics.

## <a id="static-nested-class">Static Nested Class</a>

A static nested class does **not** implicitly retain an outer instance.

```java
class BankAccount {
    static class Builder {
        BankAccount build() {
            return new BankAccount("A-01");
        }
    }
}
```

It behaves much like an ordinary class but lives in the outer class's namespace. It is useful when the nested type is logically related but does not need a particular outer object's state.

It can be created without an outer `BankAccount` instance:

```java
BankAccount.Builder builder = new BankAccount.Builder();
```

## <a id="inner-class">Inner Class</a>

A non-static nested class is an inner class and is associated with an outer instance.

```java
class BankAccount {
    private int balance;

    class BalanceView {
        int currentBalance() {
            return BankAccount.this.balance;
        }
    }
}
```

Each `BalanceView` belongs to a particular `BankAccount` instance and may access outer-instance members.

Creation syntax makes that relationship visible:

```java
BankAccount account = new BankAccount("A-01");
BankAccount.BalanceView view = account.new BalanceView();
```

That convenience also creates a lifetime/coupling relationship: keeping the inner object may keep the outer object reachable.

## <a id="local-anonymous-class">Local and Anonymous Classes</a>

Local classes are declared inside a block/method. Anonymous classes create an implementation/class instance directly in an expression without a reusable class name.

They are useful for local behavior, though lambdas are often simpler when only a functional interface implementation is required.

Anonymous classes still have their own object/`this` semantics; lambdas differ and belong in the functional-programming material.

## <a id="capture-semantics">Captured Local Variables</a>

Local/anonymous classes may capture local variables only when those variables are `final` or effectively final.

```java
int limit = 10;
Runnable r = new Runnable() {
    public void run() {
        System.out.println(limit);
    }
};
```

The capture follows value-oriented language rules rather than sharing an arbitrarily mutable local slot.

The next chapter looks at the root class shared by ordinary class instances: `java.lang.Object`.
