# equals and hashCode

`equals` and `hashCode` should not be designed independently. For hash-based collections they form **one combined contract**: hashing narrows the search; equality confirms the logical match.

## <a id="equals-hashcode-consistency">Equal Objects Share a Hash Code</a>

If equal objects return different hashes, `HashMap`/`HashSet` may search different regions and never reach the `equals` comparison.

Whenever `equals` is overridden, `hashCode` should be reviewed at the same time.

```text
equals == true
→ hashCode must match

hashCode matches
→ does not imply equals == true
```

## <a id="hash-collection-lookup">Hash Collection Lookup</a>

At the mental-model level, lookup can be understood as:

```text
hashCode
→ narrow the bucket/candidate region
        ↓
equals
→ confirm the logical match
```

Modern `HashMap` implementations have additional collision-handling details, but this is the object-level contract that matters.

## <a id="broken-contract-effects">Effects of a Broken Contract</a>

When equal objects produce different hashes, you may observe:

- a `HashSet` that appears to contain duplicates;
- `HashMap.get(equalKey)` missing an inserted entry;
- `contains` returning false even though a logically equal object is stored.

That is not a collection bug. The collection is operating under the documented assumption that keys honor the contract.

The next chapter moves from equality contracts to a human-facing object contract: `toString`.
