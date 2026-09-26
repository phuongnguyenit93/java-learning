# hashCode

`equals` can answer whether two objects are logically equal, but a `HashMap` would be inefficient if it had to compare every key with `equals`. `hashCode` gives hash-based collections a fast signal for narrowing the search.

## <a id="hashcode-contract">The hashCode Contract</a>

The most important rule is:

```text
if a.equals(b) == true
→ a.hashCode() must equal b.hashCode()
```

The reverse is not required:

```text
same hash code
↛ necessarily equal
```

Unequal objects may collide and produce the same hash code.

Repeated calls should also remain consistent while the state relevant to equality remains unchanged.

## <a id="hash-distribution">Hash Distribution</a>

A useful hash combines equality-relevant fields so common values are distributed reasonably across buckets.

The goal is **not unique hashes for every object**; that cannot be guaranteed in a finite `int` space.

```text
correctness
→ comes from the equals/hashCode contract

performance
→ is influenced by hash distribution
```

Collisions are expected. Hash tables are designed to resolve them by checking equality among candidate entries.

## <a id="mutable-key-risk">Mutable Key Risk</a>

If a field used by `equals`/`hashCode` changes after insertion into a `HashMap` or `HashSet`, the object may remain stored in a bucket chosen by its old hash while a later lookup computes a new hash.

The surprising result can be:

```text
the object is still in the collection
but contains/get/remove cannot find it
```

Prefer immutable keys, or at least avoid mutating equality/hash state while an object is used as a key.

The next chapter connects `equals` and `hashCode` as one contract.
