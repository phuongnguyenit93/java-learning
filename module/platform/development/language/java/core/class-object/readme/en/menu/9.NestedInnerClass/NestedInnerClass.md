# Nested and Inner Classes

Placing one type inside another can express close conceptual organization or access to surrounding context. Different nested-class forms have very different semantics.

## <a id="static-nested-class">Static Nested Class</a>

A static nested class does **not** implicitly retain an outer instance.

```java
class Profile {
    static class Builder { ... }
}
```

It behaves much like an ordinary class but lives in the outer class's namespace. It is useful when the nested type is logically related but does not need a particular outer object's state.

## <a id="inner-class">Inner Class</a>

A non-static nested class is an inner class and is associated with an outer instance.

```java
class Order {
    class LineView { ... }
}
```

Each `LineView` belongs to a particular `Order` instance and may access outer-instance members.

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

The next chapter looks at the common superclass of all ordinary reference types: `java.lang.Object`.
