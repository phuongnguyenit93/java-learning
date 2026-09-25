# Copy Semantics, Shallow Copy and Deep Copy

## <a id="reference-copy">Reference copy</a>
Assigning one object reference to another variable copies only the reference value. No new domain object is created; both variables identify the same object. This is the default behavior of assignment and parameter passing for reference values.

## <a id="shallow-copy">Shallow copy</a>
A shallow copy creates a new outer object but copies field values as-is. Primitive fields are independent values, while referenced mutable objects remain shared. Therefore mutating a nested list/address after a shallow copy can be visible through both outer objects.

## <a id="deep-copy">Deep copy</a>
A deep copy duplicates the mutable object graph to the depth required by the ownership contract. “Deep” is domain-specific: immutable shared objects usually need not be duplicated, cycles require care, and external resources often cannot be meaningfully copied.

## <a id="copy-strategies">Copy constructor/factory/manual mapping strategies</a>
Prefer explicit copy constructors/factories or mapping code that states which state is shared and which is duplicated. `Cloneable`/serialization tricks hide policy and can bypass invariants. A good copy API documents ownership, depth, identity, and whether derived/transient state is preserved.
