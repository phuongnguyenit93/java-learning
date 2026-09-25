# Object Creation Lifecycle

## <a id="allocation-initialization-construction">Allocation → initialization → construction mental model</a>
Conceptually, object creation allocates storage, gives fields default values, runs superclass construction, applies instance initializers, and executes constructor bodies before returning the reference. JVM implementation details may optimize allocation, but Java-visible initialization semantics must be preserved.

## <a id="constructor-dynamic-dispatch-risk">Calling overridable methods during construction</a>
Instance method dispatch remains virtual during construction. If a superclass constructor calls an overridable method, a subclass override can run before subclass fields are explicitly initialized. The override may observe default values and violate assumptions.

```java
class Parent { Parent() { hook(); } void hook() {} }
class Child extends Parent { int x = 42; @Override void hook(){ /* x may be 0 */ } }
```

Avoid invoking overridable behavior from constructors unless the contract is intentionally designed for it.

## <a id="this-escape">this escape during construction</a>
`this` escapes when the not-fully-constructed object becomes reachable elsewhere, for example by registering a listener, starting a thread, or storing itself in shared state. Other code may observe broken invariants. Keep construction private until initialization completes, then publish the object.
