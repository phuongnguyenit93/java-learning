# The equals/hashCode Contract

Suppose two different `UserId` objects both hold `"U-100"`. If the application considers them the same user ID, a `HashSet` should recognize the second as already present, and a `HashMap` should find a value stored under the first. That behavior depends on **two methods cooperating**: `hashCode` helps a hash-based collection find candidates, and `equals` decides whether a candidate represents the same logical value.

The preceding chapters introduced the methods individually. Here we follow an insertion and lookup, then change one condition at a time to see what goes wrong when their promises disagree or a key changes after insertion.

## <a id="equals-hashcode-consistency">Equal Objects Share a Hash Code</a>

The joint contract has one essential implication: **if `a.equals(b)` is true, `a.hashCode()` and `b.hashCode()` must return the same integer**. Otherwise a hash-based collection may search where `b`'s hash points instead of where it stored `a`, never reaching the equality check.

Consider two separately constructed IDs with the same value:

```text
first  = new UserId("U-100")
second = new UserId("U-100")

first == second                   → false (different objects)
first.equals(second)             → true  (same logical ID)
first.hashCode() == second.hashCode() → must be true

two hashes equal                 ↛ two objects necessarily equal
```

The final line matters: different values may have the same hash (*a collision*). When that happens, the collection must still use `equals` to distinguish them. A hash code is a routing hint, not a unique ID or a verdict of equality.

### KEEP BOTH METHODS BASED ON THE SAME LOGICAL VALUE

If the `value` field defines equality, deriving the hash from `value` makes equal IDs hash alike. The runnable `UserId` class in the next section uses `Objects.equals(value, that.value)` and `value.hashCode()` to make this relationship explicit. Its `value` is `final`, so its equality meaning cannot change after construction.

If equality later changes to depend on both `namespace` and `value`, review the hash calculation in the same change. The formal rule does **not** demand that every equality field appear in `hashCode`: a constant hash would still give equal objects equal hashes. But including useful equality fields usually distributes unequal keys better and reduces unnecessary equality checks. Also, repeated hashes for an object must agree while its equality-relevant information remains unchanged.

## <a id="hash-collection-lookup">Hash Collection Lookup</a>

Think of a hash-based collection as an index with candidate groups. For a **non-null key**, its ordinary insertion and lookup can be understood in this order:

```text
add/put(original)                        contains/get(equal lookup object)
  │                                          │
  ├─ calculate original.hashCode()          ├─ calculate lookup.hashCode()
  ├─ choose a candidate region              ├─ search compatible region
  └─ store original (or key/value)          ├─ compare possible matches with equals
                                             └─ return match or absence
```

If the lookup object hashes to the same region as the stored object, an equality check can confirm they represent the same logical key. If several unequal keys collide, `equals` distinguishes them within that candidate group. This is a learner-facing model of `HashSet`/`HashMap`, not a promise about bucket counts, collision algorithms, or every method call in a particular JDK implementation.

### A RUNNABLE `HashSet` AND `HashMap` TRACE

Save the following as `HashLookupDemo.java` and run it with Java 21. Both lookups deliberately construct **new** objects rather than reuse the stored reference.

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HashLookupDemo {
    static final class UserId {
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
            return value.hashCode();
        }
    }

    public static void main(String[] args) {
        UserId stored = new UserId("U-100");
        UserId lookup = new UserId("U-100");

        Set<UserId> ids = new HashSet<>();
        ids.add(stored);

        Map<UserId, String> names = new HashMap<>();
        names.put(stored, "An");

        System.out.println(stored == lookup);
        System.out.println(stored.equals(lookup));
        System.out.println(stored.hashCode() == lookup.hashCode());
        System.out.println(ids.contains(lookup));
        System.out.println(ids.add(lookup));
        System.out.println(names.get(lookup));
    }
}
```

Expected output:

```text
false
true
true
true
false
An
```

Trace the results in order:

1. `stored == lookup` is `false`: the constructor ran twice, producing two instances.
2. `equals` is `true`: both instances contain `"U-100"`; their `hashCode()` results are equal because both hash that same string.
3. `ids.add(stored)` inserted the first ID. `ids.contains(lookup)` follows its compatible hash region and finds an equal element, so it returns `true`. A second `add` finds that equal value and returns `false` rather than adding a duplicate.
4. `names.put(stored, "An")` associates the first ID with the name. `names.get(lookup)` finds the equal key through the same cooperating hash/equality checks and returns `"An"`.

The caller needs an **equivalent logical key**, not the exact reference used for insertion. `HashSet` also permits a `null` element and `HashMap` permits a `null` key; implementations handle these specially, so the non-null diagram is not a claim that `null.hashCode()` is invoked.

## <a id="broken-contract-effects">Effects of a Broken Contract</a>

Now keep equality based on `value` but deliberately calculate the hash from an unrelated number. Equal IDs will be directed to different candidate regions. This runnable counterexample makes the failure deterministic instead of relying on whether two default `Object.hashCode()` values happen to differ.

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class BrokenContractDemo {
    static final class BrokenUserId {
        private final String value;
        private final int unrelatedHash;

        BrokenUserId(String value, int unrelatedHash) {
            this.value = Objects.requireNonNull(value);
            this.unrelatedHash = unrelatedHash;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof BrokenUserId that
                    && value.equals(that.value));
        }

        @Override
        public int hashCode() {
            return unrelatedHash; // intentionally violates the contract
        }
    }

    public static void main(String[] args) {
        BrokenUserId first = new BrokenUserId("U-100", 1);
        BrokenUserId equal = new BrokenUserId("U-100", 2);

        Set<BrokenUserId> ids = new HashSet<>();
        ids.add(first);
        System.out.println(first.equals(equal));
        System.out.println(first.hashCode() == equal.hashCode());
        System.out.println(ids.contains(equal));
        System.out.println(ids.add(equal));
        System.out.println(ids.size());

        Map<BrokenUserId, String> names = new HashMap<>();
        names.put(first, "An");
        System.out.println(names.get(equal));
    }
}
```

Output with Java 21 `HashSet` and `HashMap`:

```text
true
false
false
true
2
null
```

The first line proves the two keys are logically equal. Their hashes are `1` and `2`, so the second lookup follows a different candidate region and does not encounter the stored first key. The set incorrectly appears to admit two logical copies (`size()` is `2`), and the map cannot retrieve `"An"` using the equal key. These outputs illustrate a **key contract violation**, not an error in the collections.

### THE COMMON ACCIDENTAL VERSION

Another common mistake overrides `equals` for a logical value but forgets `hashCode`, leaving the identity-oriented implementation inherited from `Object`. Different instances **may** then receive different hashes despite being equal. This outcome is not guaranteed for every pair, which is why the deliberate example above is more reliable for learning and testing.

The repair is to derive `hashCode` from `value`, exactly as in `HashLookupDemo`:

```java
@Override
public int hashCode() {
    return value.hashCode();
}
```

Changing the method restores the required implication for this equality design. A collision between **unequal** values would still be legal; the contract does not demand unique hashes.

## <a id="mutable-key-risk">Mutable Key Risk</a>

There is a second kind of failure: `equals` and `hashCode` may be perfectly consistent at every moment, yet a key can become hard to find **after its equality-relevant state changes while it is stored**. Hash-based collections do not automatically relocate an entry when code edits a field on its key.

```text
insert key whose value is "A" → hash 65 → stored along path for 65
change that SAME object's value to "B" → hash 66
look up the key again → search follows path for 66
stored entry may still be on path for 65 → lookup misses it
```

Here the string hash values are known: `"A".hashCode()` is `65` and `"B".hashCode()` is `66`. This runnable Java 21 example observes both a set and a map:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MutableKeyDemo {
    static final class UserKey {
        String value; // mutable equality-relevant state

        UserKey(String value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof UserKey that
                    && value.equals(that.value));
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static void main(String[] args) {
        UserKey key = new UserKey("A");
        Set<UserKey> ids = new HashSet<>();
        Map<UserKey, String> names = new HashMap<>();
        ids.add(key);
        names.put(key, "An");

        System.out.println(ids.contains(key));
        System.out.println(names.get(key));

        key.value = "B";
        System.out.println(key.hashCode());
        System.out.println(ids.contains(key));
        System.out.println(names.get(key));
        System.out.println(ids.iterator().next() == key);
        System.out.println(names.keySet().iterator().next() == key);
    }
}
```

Output with Java 21 `HashSet` and `HashMap`:

```text
true
An
66
false
null
true
true
```

Before mutation, both lookups succeed. After `key.value = "B"`, **the same reference** now reports hash `66` instead of the `65` used during insertion. The set's `contains` and map's `get` miss the original entry, but iterating over each collection still reveals that exact object reference. This is why even `remove(key)` may fail after mutation. The Java `Map` contract explicitly leaves behavior unspecified when a key is modified in a way that changes equality while stored; this concrete output must not be treated as a promise for every map implementation.

### KEEP HASH KEYS STABLE WHILE STORED

Prefer keys whose equality-relevant state is immutable. If a key must change, remove it from every affected hash-based collection **before** the change, mutate it, then insert it again under its new meaning. Once the key has already been mutated, normal removal with that key may no longer work; rebuilding the affected collection with valid stable keys may be necessary.

Using `final` for a reference helps only if the data being hashed cannot change through that reference; a `final` field pointing to a mutable object does not by itself make a stable key. Before using a domain object as a key, ask: **will every value used by its `equals`/`hashCode` stay stable for as long as this collection stores it?**

### References

- [Oracle Java 21 `Object` contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html) — formal equality and hash requirements.
- [Oracle Java 21 `HashSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html) — hash-backed set and its equality-based operations.
- [Oracle Java 21 `Map`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html) — the warning about mutating keys while stored.

The next chapter moves from collection equality to a human-facing contract: **how an object should describe itself for diagnostics**.
