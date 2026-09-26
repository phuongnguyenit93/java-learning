# Class and Object

Java uses a `class` to describe **a kind of object with state and behavior**, while an `object` is a real runtime instance. This lets Java keep data together with the operations and rules that are responsible for that data instead of treating every value as unrelated global state.

Keep a small running example such as `BankAccount`: the class defines shared structure and rules; each account object has its own identity and state. Later chapters evolve the same mental model through construction, initialization, copying, aliasing, and immutability.

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

The main terminology fits together like this:

```text
class
→ defines the type, state shape and behavior

object / instance
→ one runtime entity created from that type

constructor / initialization
→ establish the object's initial valid state

this / super
→ express current-instance and superclass construction/member context

static / instance
→ distinguish class-level state/behavior from per-object state/behavior

reference / aliasing / copying / immutability
→ explain how objects are reached, shared, duplicated or protected from mutation
```

## <a id="class-object-model">Class vs Object</a>

A `class` defines a type and its shared implementation: fields, methods, constructors, nested types, and initialization logic.

An `object` is a runtime instance with:

- its own identity;
- its own instance state;
- behavior determined by the class/runtime type.

```java
BankAccount first = new BankAccount("A-01");
BankAccount second = new BankAccount("A-02");
```

Both objects use the same `BankAccount` class definition but remain distinct instances with different identities and potentially different state.

### Why group state and behavior?

Suppose an account balance could be changed by arbitrary code through an exposed integer. Every caller would need to remember all rules such as "do not withdraw below zero". A class can instead keep the state and the operations that protect its invariants together:

```java
class BankAccount {
    private int balance;

    void withdraw(int amount) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException();
        }
        balance -= amount;
    }
}
```

The important idea is not merely that Java has `class` syntax. The object becomes the owner of state and the rules that keep that state meaningful. Later modules go deeper into encapsulation and object-oriented design; this module first establishes the object model and lifecycle needed to understand them.

## <a id="fields-methods-state">State, Behavior and Identity</a>

Instance fields represent per-object state. Instance methods operate on the current object through the implicit `this` reference.

Static members belong to class-level context instead of per-object state.

Two objects may contain equal field values while still having different identities. Equality vs identity is explored further in the `object-contract` module.

## <a id="object-reference-lifecycle">References and Object Lifetime</a>

A reference variable stores a **reference value**, not the object “inside the variable”. Several variables may refer to the same object.

An object may outlive a local variable if other reachable references still exist. Garbage collection is based on reachability rather than lexical scope alone.

External-resource lifetime is different: files and sockets require deterministic cleanup rather than waiting for GC.

The next chapter starts at object creation: what should a constructor establish before the object becomes usable?
