# Class and Object

Java uses a `class` to describe **a kind of object with state and behavior**, while an `object` is a real runtime instance. This distinction is the foundation for constructors, `this`, `static`, initialization order, aliasing, copying, and immutability.

Keep a small running example such as `Profile` or `BankAccount`: the class defines shared structure and rules; each object has its own identity and state.

Roadmap:

```text
How do class and object differ?
Class & Object
        ↓
How is a valid object created?
Constructor
        ↓
What do this / super mean?
this / super
        ↓
Who may access which members?
Access Modifiers
        ↓
Which members belong to the class vs each instance?
static / final
        ↓
Where and in what order does initialization happen?
Initialization Blocks → Initialization Order
        ↓
What is the complete object-creation lifecycle?
Object Creation Lifecycle
        ↓
What context can nested/inner classes capture?
Nested / Inner Classes
        ↓
What common contract does every object inherit?
Object Class
        ↓
How does Java model a closed set of typed constants?
Enum
        ↓
What does copying an object actually copy?
Copy Semantics
        ↓
What happens when several references share mutable state?
Aliasing & Mutability
        ↓
How can objects be made safer to share?
Immutability & Defensive Copy
```

## <a id="class-object-model">Class vs Object</a>

A `class` defines a type and its shared implementation: fields, methods, constructors, nested types, and initialization logic.

An `object` is a runtime instance with:

- its own identity;
- its own instance state;
- behavior determined by the class/runtime type.

```java
Profile first = new Profile("An");
Profile second = new Profile("Binh");
```

Both objects use the same class definition but remain distinct instances with different state.

## <a id="fields-methods-state">State, Behavior and Identity</a>

Instance fields represent per-object state. Instance methods operate on the current object through the implicit `this` reference.

Static members belong to class-level context instead of per-object state.

Two objects may contain equal field values while still having different identities. Equality vs identity is explored further in the `object-contract` module.

## <a id="object-reference-lifecycle">References and Object Lifetime</a>

A reference variable stores a **reference value**, not the object “inside the variable”. Several variables may refer to the same object.

An object may outlive a local variable if other reachable references still exist. Garbage collection is based on reachability rather than lexical scope alone.

External-resource lifetime is different: files and sockets require deterministic cleanup rather than waiting for GC.

The next chapter starts at object creation: what should a constructor establish before the object becomes usable?
