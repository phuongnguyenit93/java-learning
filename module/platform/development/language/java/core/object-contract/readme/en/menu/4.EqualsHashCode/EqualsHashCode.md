# equals and hashCode Together

## <a id="equals-hashcode-consistency">Equal objects must share hash code</a>
Overriding `equals` without a compatible `hashCode` violates the contract used by `HashMap`, `HashSet`, caches, and many frameworks. The reverse is not required: same hash does not imply equality.

## <a id="hash-collection-lookup">HashMap/HashSet lookup mechanics boundary</a>
Conceptually a hash collection uses the hash to narrow candidate locations and then uses equality to confirm the key/element. Modern implementations contain extra collision-handling details, but the learning invariant is hash narrows, equals decides logical match.

## <a id="broken-contract-effects">Observable failures from broken contract</a>
If equal objects return different hashes, a `HashSet` can appear to contain duplicates and a `HashMap` lookup using an equal key can miss the stored entry. These are not collection bugs; the collection is relying on a broken key contract.
