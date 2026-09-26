# java.lang.Object

`java.lang.Object` is the root class of Java's ordinary class hierarchy. Every class other than `Object` itself has `Object` somewhere in its superclass chain, which gives ordinary class instances a common baseline contract. The default implementations are not always appropriate for domain semantics.

## <a id="object-root-type">Object as the Root Reference Type</a>

An `Object` reference can refer to an instance of any class:

```java
Object value = new BankAccount("A-01");
```

But the static type `Object` exposes only the `Object` contract. Accessing `BankAccount`-specific behavior requires appropriate type information/casting.

Primitives are not subtypes of `Object`; wrapper types let primitive values participate in reference-based APIs.

## <a id="object-core-methods">Core Object Methods</a>

Important methods include:

- `getClass()`;
- `toString()`;
- `equals()`;
- `hashCode()`;
- `wait/notify/notifyAll`;
- legacy `clone()`;
- legacy finalization behavior.

`equals`, `hashCode`, and `toString` are explored deeply in the `object-contract` module.

## <a id="clone-finalize-boundary">clone/finalize Boundary</a>

`Cloneable`/`Object.clone()` has awkward semantics for deep object graphs, constructors, and invariants. Prefer explicit copy constructors, factories, or mapping when possible.

Finalization is not a reliable resource-management mechanism. Prefer `AutoCloseable`/try-with-resources or other explicit cleanup designs.

The next chapter looks at a special class form: `enum`.
