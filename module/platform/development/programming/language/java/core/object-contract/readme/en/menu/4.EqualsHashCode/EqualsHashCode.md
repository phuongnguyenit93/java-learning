# equals and hashCode

`equals` and `hashCode` should not be designed independently. For hash-based collections they form **one combined contract**: hashing narrows the candidates, while equality confirms the logical match.

The previous chapters studied each method separately. This chapter focuses on **how they cooperate during lookup** and on observable runtime failures when the contract is broken.

## <a id="equals-hashcode-consistency">Equal Objects Share a Hash Code</a>

If equal objects return different hashes, `HashMap`/`HashSet` may search different regions and never reach the `equals` comparison.

Whenever `equals` is overridden, `hashCode` should be reviewed at the same time.

```text
equals == true
→ hashCode must match

hashCode matches
→ does not imply equals == true
```

### USE THE SAME EQUALITY STATE

A design is easier to reason about when both methods use the same fields:

```java
final class UserId {
    private final String value;

    UserId(String value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof UserId that
                && Objects.equals(value, that.value));
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
```

If equality later changes from `value` to `namespace + value`, both methods must be reconsidered together.

## <a id="hash-collection-lookup">Hash Collection Lookup</a>

At the mental-model level, lookup can be understood as:

```text
lookup key
    ↓
key.hashCode()
    ↓
narrow bucket/candidate region
    ↓
inspect compatible candidates
    ↓
equals(...)
    ↓
match / no match
```

This is a **boundary model**, not a promise about every internal detail of a particular `HashMap` version. Implementations may optimize collision handling; your object only needs to honor the public contract collections rely on.

### EVIDENCE WITH `HashSet`

```java
Set<UserId> ids = new HashSet<>();
ids.add(new UserId("U-100"));

System.out.println(ids.contains(new UserId("U-100")));
```

For this to print `true`, two distinct instances need both:

```text
the same logical equality
        +
a consistent hash contract
```

The caller does not need the original reference to look up an equivalent logical value.

### `HashMap` USES THE SAME CONTRACT

```java
Map<UserId, String> names = new HashMap<>();
names.put(new UserId("U-100"), "An");

String name = names.get(new UserId("U-100"));
```

Callers normally expect `name` to be `"An"`. That expectation only makes sense when the key type has coherent equality and hashing semantics.

## <a id="broken-contract-effects">Effects of a Broken Contract</a>

A classic bug is overriding `equals` but keeping the inherited `hashCode` behavior:

```java
final class BrokenUserId {
    private final String value;

    BrokenUserId(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BrokenUserId that
                && Objects.equals(value, that.value);
    }

    // intentionally no hashCode override
}
```

Two objects may then satisfy:

```text
a.equals(b) == true

but

a.hashCode() != b.hashCode()
```

Observable symptoms include:

- a `HashSet` that appears to contain duplicate logical values;
- `HashMap.get(equalKey)` missing an inserted entry;
- `contains` returning false even though a logically equal object is stored.

### THIS DOES NOT MEAN THE COLLECTION IS BROKEN

The collection operates under the documented assumption that keys honor their contract. It cannot reconstruct your domain equality semantics after the key violates that assumption.

### MUTATION CAN BREAK AN INITIALLY CORRECT CONTRACT

Even correct methods can become operationally unsafe if equality state changes after insertion:

```text
correct equals/hashCode implementation
        +
mutable key state
        ↓
lookup can still fail
```

So key design asks more than “are the methods implemented correctly?” It also asks:

> Does equality-relevant state remain stable while the object is stored in the collection?

The next chapter moves from collection equality to a human-facing contract: **how an object should describe itself for diagnostics**.
