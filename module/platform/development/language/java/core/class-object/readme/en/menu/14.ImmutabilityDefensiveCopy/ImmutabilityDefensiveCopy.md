# Immutability and Defensive Copy

## <a id="immutable-object-design">Immutable object design</a>
An immutable object's observable state never changes after successful construction. Typical design keeps fields private/final, establishes invariants in construction, prevents mutator methods, and avoids exposing mutable internal objects. `final` on the reference alone does not make a nested object immutable.

## <a id="defensive-copy-input">Defensive copy on input</a>
If a constructor/method accepts a mutable value that becomes internal state, copy it when the object should own an independent snapshot. Otherwise the caller can mutate the supplied object later and silently change internal state.

## <a id="defensive-copy-output">Defensive copy on output</a>
Returning a mutable internal object leaks an alias. Return an immutable representation, an independent copy, or a carefully documented view depending on the contract. `Collections.unmodifiableList(internal)` is a read-only view, not necessarily an immutable snapshot.

## <a id="deep-immutability">Shallow vs deep immutability</a>
An object with final fields can still be only shallowly immutable if those fields reference mutable objects that other aliases can change. Deep immutability requires the reachable state relevant to the abstraction to be immutable or exclusively owned and never mutated after construction.
