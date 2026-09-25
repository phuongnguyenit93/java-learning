# static and final

## <a id="static-vs-instance">Static vs instance members</a>
Instance members require an object receiver and can access that object's state. Static members belong to the class context and should be accessed through the class name for clarity. A static method cannot directly use `this` or instance fields without an explicit object reference.

## <a id="final-variable-reference">final primitive/reference semantics</a>
A `final` variable can be assigned once. For a primitive this fixes the primitive value; for a reference it fixes which object the variable refers to, not the mutability of that object.

```java
final List<String> names = new ArrayList<>();
names.add("A"); // allowed
// names = new ArrayList<>(); // not allowed
```

## <a id="static-initialization">Static member initialization</a>
Static fields and static initializer blocks run as part of class initialization, once per initialized `Class` object/defining loader. Their order follows textual initialization order after default values are established. Detailed JVM triggering belongs to classloader/JVM curriculum.

## <a id="constants-design">Constants and compile-time constants</a>
`static final` does not automatically mean compile-time constant. Primitive/String fields initialized with constant expressions can be inlined into client bytecode, which has compatibility implications when a library constant changes. Constants should also represent truly immutable values, not mutable objects behind final references.
