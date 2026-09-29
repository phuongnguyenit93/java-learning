# hashCode

`equals` can answer whether two objects are logically equal, but a `HashMap` would be inefficient if it had to compare every key with `equals`. `hashCode` gives hash-based collections a fast signal for narrowing the candidate region.

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

`hashCode` should use the **same equality-relevant state** as `equals`.

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

## <a id="hash-distribution">Hash Distribution</a>

A useful hash combines equality-relevant fields so common inputs distribute reasonably across bucket/candidate regions.

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

`hashCode` primarily exists to support Java hash-based object contracts.

## <a id="mutable-key-risk">Mutable Key Risk</a>

If a field used by `equals`/`hashCode` changes after insertion into a `HashMap` or `HashSet`, the object may remain stored in a region chosen by its old hash while later lookup computes a new hash.

```java
final class UserKey {
    String value;

    UserKey(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserKey that
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

UserKey key = new UserKey("U-100");
Set<UserKey> set = new HashSet<>();
set.add(key);

key.value = "U-200";

System.out.println(set.contains(key)); // may now be false
```

Mental model:

```text
insert
hash("U-100")
→ candidate region A

mutate key
hash("U-200")
→ lookup region B

the object is still stored according to region A
```

The result can be that `contains/get/remove` cannot find an object that is physically still stored in the collection.

### SAFE PRACTICE

Prefer immutable keys, or at least do not mutate equality/hash state while an object is being used as a key.

The next chapter combines `equals` and `hashCode` into **one operational contract** and shows what happens when the two methods disagree.
