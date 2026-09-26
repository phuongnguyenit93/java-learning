# Object Creation Lifecycle

`new Child()` looks like one expression, but object creation includes allocation, default initialization, constructor chaining, field/block initialization, and finally a usable object.

## <a id="allocation-initialization-construction">Object Creation Stages</a>

A useful mental model is:

```text
allocate object memory
        ↓
fields receive default zero/null/false values
        ↓
superclass construction
        ↓
instance field initializers / initializer blocks
        ↓
current-class constructor body
        ↓
reference returned to caller if construction succeeds
```

Allocated memory is not the same thing as a valid constructed object. Invariants should be considered established only after the constructor chain completes successfully.

## <a id="constructor-dynamic-dispatch-risk">Dynamic Dispatch During Construction</a>

Instance method calls still use dynamic dispatch inside constructors.

If a superclass constructor calls an overridable method, subclass behavior may execute **before subclass state is initialized**.

```java
class Parent {
    Parent() { print(); }
    void print() { }
}

class Child extends Parent {
    private String value = "ready";
    @Override void print() { System.out.println(value); }
}
```

`print()` may observe `value == null` when invoked from `Parent()`.

A strong default is to avoid overridable method calls from constructors.

## <a id="this-escape">this Escape</a>

`this` escape happens when a reference to the under-construction object is published before construction completes.

Examples include registering `this` in a global registry or listener from the constructor.

External code may then observe partially initialized state. In concurrent code, premature publication creates even more serious visibility hazards.

Next we move from lifecycle to type organization: nested and inner classes.
