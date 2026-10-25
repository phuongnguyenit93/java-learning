# Object as the Common Root Class

`java.lang.Object` is the root class of Java's ordinary class hierarchy. Every class other than `Object` itself has `Object` somewhere in its superclass chain, which gives ordinary class instances a common baseline contract. Learning this model explains both the broadest ordinary reference type and the core methods inherited by normal classes before their deeper behavioral contracts are studied elsewhere. The default implementations inherited from `Object` are not always appropriate for domain semantics, so some classes need behavior defined according to the relevant contracts.

## <a id="object-root-type">Object as the Root Reference Type</a>

An `Object` reference can refer to an instance of any class:

```java
Object value = new BankAccount("A-01");
```

But the static type `Object` exposes only the `Object` contract. Accessing `BankAccount`-specific behavior requires appropriate type information/casting.

Primitives are not subtypes of `Object`; wrapper types let primitive values participate in reference-based APIs.

## <a id="object-core-methods">Core Object Methods</a>

Important methods include:

- `getClass()` — reports the runtime class of the object;
- `toString()` — provides a textual representation hook;
- `equals()` — defines logical-equality behavior when overridden correctly;
- `hashCode()` — supplies a hash value that must remain consistent with `equals()`;
- `wait()/notify()/notifyAll()` — low-level monitor coordination, owned by concurrency material;
- `clone()` — a legacy copying mechanism with difficult semantics;
- finalization behavior — legacy cleanup behavior that should not be used for resource management.

`equals`, `hashCode`, and `toString` are explored deeply in the `object-contract` module. `getClass()` becomes more important in Reflection, while `wait/notify` belongs to concurrency.

## <a id="clone-finalize-boundary">clone/finalize Boundary</a>

`Cloneable`/`Object.clone()` has awkward semantics for deep object graphs, constructors, and invariants. Prefer explicit copy constructors, factories, or mapping when possible.

Finalization is not a reliable resource-management mechanism. Prefer `AutoCloseable`/try-with-resources or other explicit cleanup designs.

The next chapter looks at a special class form: `enum`.
