# Initialization Order

Many construction bugs come from code that is correct in isolation but runs **earlier or later than expected**. Java defines a specific order for class and instance initialization.

## <a id="class-initialization-order">Static Initialization Order</a>

When a class is initialized, its superclass is initialized first when required, then the class's static fields/blocks run in textual order.

```text
superclass static initialization
        ↓
subclass static fields/blocks in source order
```

Class loading/linking details belong in the classloader module; here the key point is when static state becomes initialized.

## <a id="instance-initialization-order">Instance Initialization Order</a>

Within one class, instance field initializers and instance blocks run in textual order before that class's constructor body.

```text
default zero/null state
→ field initializer / instance block
→ constructor body
```

Superclass construction still happens before the subclass portion.

## <a id="inheritance-initialization-order">Initialization Across Inheritance</a>

For `new Child()`:

```text
class initialization if needed
        ↓
Parent instance initialization
        ↓
Parent constructor body
        ↓
Child instance initialization
        ↓
Child constructor body
```

This explains why calling overridable methods too early is dangerous: subclass behavior may run before subclass fields have their intended initialized values.

A small trace makes the order observable:

```java
class Trace {
    static int log(String step) {
        System.out.println(step);
        return 0;
    }
}

class Parent {
    int parentField = Trace.log("parent field");
    { Trace.log("parent block"); }
    Parent() { Trace.log("parent constructor"); }
}

class Child extends Parent {
    int childField = Trace.log("child field");
    { Trace.log("child block"); }
    Child() { Trace.log("child constructor"); }
}
```

Assuming class initialization has already happened, `new Child()` prints the parent field/block/constructor steps before the child field/block/constructor steps. The trace is evidence for the lifecycle rather than a replacement for the rules above.

The next chapter views this as one complete object-creation lifecycle and examines `this` escape.
