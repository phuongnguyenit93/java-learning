# hashCode and Hash-Based Lookup

`equals` can answer whether two objects are logically equal, but a `HashMap` would be inefficient if it had to compare every key with `equals`. `hashCode` gives hash-based collections a fast input they can use to narrow the set of candidates that may need equality checks.

Importantly, `hashCode` **does not replace `equals`**. It helps find likely candidates more efficiently.

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

Repeated calls must also remain consistent while the state relevant to equality remains unchanged.

The contract does not require the same object to keep the same hash value across separate JVM executions. Do not persist `hashCode()` as a durable identifier and expect to reuse it in another process.

### DEFAULT `Object.hashCode()`

If a class does not override `hashCode`, the inherited implementation is compatible with identity-based equality from `Object`. Once logical equality is changed by overriding `equals`, the inherited hash behavior may no longer satisfy the new equality relation.

That is why the practical design rule is:

```text
override equals
→ review/override hashCode as part of the same design change
```

### HOW SHOULD `hashCode` BE IMPLEMENTED?

The hard requirement is behavioral: **every pair of objects that `equals` considers equal must produce the same hash code**. A straightforward way to achieve that is to derive `hashCode` from the same equality-relevant state as `equals`.

```java
@Override
public int hashCode() {
    return Objects.hash(value);
}
```

With multiple fields:

```java
@Override
public int hashCode() {
    return Objects.hash(countryCode, number);
}
```

`Objects.hash` favors readability. Performance-sensitive code may choose another calculation, but the equality contract must remain the same.

Using every equality field is not itself a contract requirement. For example, if `equals` compares `countryCode + number`, a `hashCode` based only on `countryCode` can still be correct because equal objects still hash alike. It is usually a poor design, however, because many unequal objects produce the same hash and force the collection to examine more candidates.

```text
required for correctness
→ equal objects must have equal hashes

useful for distribution
→ include enough equality-relevant state to spread unequal values reasonably
```

## <a id="hash-distribution">Hash Distribution</a>

A useful hash combines equality-relevant fields so common inputs are distributed well enough for a hash-based collection to narrow candidate groups effectively.

The goal is **not a unique hash for every object**; uniqueness cannot be guaranteed in a finite `int` space.

```text
correctness
→ comes from the equals/hashCode contract

performance
→ is influenced by hash distribution
```

### COLLISION IS NOT A BUG

Different objects may deliberately or accidentally share a hash:

```java
final class DemoKey {
    private final int id;

    DemoKey(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return 1; // deliberately poor distribution
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DemoKey that && id == that.id;
    }
}
```

A hash collection can remain correct because it still checks equality among candidate entries. Poor distribution mainly hurts performance by concentrating too many candidates in the same region.

### A HASH CODE IS NOT AN ID OR SECURITY TOKEN

Do not infer:

```text
same hash code → objects are equal?          false
hashCode → secure fingerprint?               false
hashCode → persistent identity?              false
```

Keep reference identity and hashing separate:

```text
reference identity (`==`)
→ are these references pointing to the exact same object?

hashCode()
→ input used by a hash-based collection to narrow where it must search
```

Two distinct objects can have the same hash code, and two distinct instances that are logically equal are required to have the same hash code. A hash code therefore cannot tell you whether two references have the same object identity.

`hashCode` primarily exists to support Java hash-based object contracts.

The next chapter combines `equals` and `hashCode` into **one operational contract** and shows what happens when the two methods disagree.
