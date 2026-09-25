# hashCode Contract

## <a id="hashcode-contract">hashCode contract</a>
If two objects are equal according to `equals`, they must return the same hash code. Unequal objects may share a hash code; collisions are expected. During one execution, repeated calls should remain consistent while equality-relevant state remains unchanged.

## <a id="hash-distribution">Hash distribution and performance</a>
A good hash combines equality-relevant fields so common objects distribute reasonably across buckets. Perfect uniqueness is neither possible nor required. Correctness comes from the equality contract; distribution affects hash-table performance.

## <a id="mutable-key-risk">Mutable fields used in hashCode</a>
If a key's equality/hash fields mutate after insertion into a hash-based collection, its bucket location no longer matches its current hash and lookup/removal can fail. Prefer immutable keys or never mutate key state while stored.
