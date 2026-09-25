# Object Class

## <a id="object-root-type">Object as root reference type</a>
Every class ultimately derives from `java.lang.Object` (arrays are also reference objects with Object-compatible behavior). This gives a common minimal API and permits an `Object` reference to point at any object, at the cost of losing compile-time knowledge of more specific members.

## <a id="object-core-methods">getClass/toString/equals/hashCode overview</a>
`getClass` exposes runtime class identity; `equals` and `hashCode` define logical equality/hash contracts when overridden; `toString` provides a diagnostic representation. The full equality/ordering contracts belong to the object-contract module, but every class inherits these methods.

## <a id="clone-finalize-boundary">Legacy clone/finalization boundary and safer alternatives</a>
`Object.clone` supports a shallow field copy under the `Cloneable` protocol but has awkward constructor/invariant semantics; copy constructors/factories are usually clearer. Finalization is deprecated for removal and must not be used for normal resource management. Prefer deterministic cleanup (`AutoCloseable`) and explicit copy policies.
