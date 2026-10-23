# hashCode and Hash-Based Lookup

Imagine looking up a customer by a key such as `("VN", 41)`. A `HashMap` can hold thousands of keys; comparing the requested key with every stored key would waste work. Java's `hashCode()` method returns an `int` that a hash-based collection can use to narrow the area it searches.

The hash code is a **search hint, not an equality answer**. `equals` determines whether two keys represent the same logical value; `hashCode` helps the collection reach a small group of possible matches. This chapter establishes the hash rule and why collisions are normal. The next chapter studies what happens when equality and hashing disagree during actual collection use.

## <a id="hashcode-contract">The hashCode Contract</a>

Every Java object has a `hashCode()` method inherited from `Object` unless its class overrides it. The method returns a 32-bit signed integer. It does not return an object identifier or a guaranteed unique number.

The essential rule connects hashing to the logical equality learned in the previous chapter:

```text
if a.equals(b) == true
→ a.hashCode() must equal b.hashCode()
```

This direction matters because a collection must be able to search the same candidate region when given a different object representing the same key. The reverse is **not** required:

```text
a.hashCode() == b.hashCode()
↛ a.equals(b) must be true

different hash codes
→ a.equals(b) cannot be true if the contract is honored
```

Unequal objects can share a hash code; that is called a **hash collision**. A collision alone is not a contract violation.

Repeated calls on the *same object* during one application execution must also produce the same hash code while the state used by `equals` remains unchanged. This rule does not promise stability if equality-relevant state changes.

The contract does not require a hash value to remain the same across separate application executions. Do not store `hashCode()` as a durable identifier and expect to reuse it in another process.

### DEFAULT `Object.hashCode()` AND LOGICAL EQUALITY

By default, `Object.equals` checks reference identity: two distinct instances are not equal even if their fields contain the same values. The inherited `Object.hashCode` works with that identity-based definition. It is **not** guaranteed to expose a memory address, nor to differ for every distinct object.

If a class changes `equals` to compare logical values but inherits `Object.hashCode`, its new equal instances may return different hashes. That is why overriding `equals` requires reviewing and normally overriding `hashCode` as part of the same design change.

### IMPLEMENTING A VALUE KEY

For a customer key, say the pair `countryCode` and `number` defines logical equality. Deriving `hashCode` from that same pair makes the contract easy to inspect. Save this complete Java 21 example as `HashCodeDemo.java` and run it:

```java
import java.util.Objects;

public class HashCodeDemo {
    static final class CustomerKey {
        private final String countryCode;
        private final int number;

        CustomerKey(String countryCode, int number) {
            this.countryCode = Objects.requireNonNull(countryCode);
            this.number = number;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof CustomerKey that
                    && number == that.number
                    && countryCode.equals(that.countryCode));
        }

        @Override
        public int hashCode() {
            return Objects.hash(countryCode, number);
        }
    }

    public static void main(String[] args) {
        CustomerKey a = new CustomerKey("VN", 41);
        CustomerKey b = new CustomerKey("VN", 41);

        System.out.println(a == b);                       // false
        System.out.println(a.equals(b));                   // true
        System.out.println(a.hashCode() == b.hashCode());  // true
    }
}
```

The constructor makes two separate objects, so `a == b` is false. Both contain `("VN", 41)`, so `equals` returns true. Their `hashCode` methods calculate from the same pair and therefore return the same integer. Those last two `true` results demonstrate the forward implication of the contract, **not** a claim that equal hash codes prove equality.

`Objects.hash` provides a readable way to combine field hashes; it is a convenience, not the only valid formula. For a single object field, `Objects.hash(value)` also produces a valid value-based hash, although its result is not generally identical to `value.hashCode()`. Performance-sensitive code can choose another correct calculation.

Using every equality field is not itself a contract requirement. For example, if `equals` compares `countryCode + number`, a `hashCode` based only on `countryCode` can still be correct because equal objects still hash alike. It is usually a poor design, however, because many unequal objects produce the same hash and force the collection to examine more candidates.

The rule to keep is **correctness first**: equal keys must hash alike. Including enough stable equality-relevant fields is then a **performance choice** to reduce unnecessary collisions.

## <a id="hash-distribution">Hash Distribution</a>

A hash-based collection groups entries into **buckets** (candidate regions) derived from their hash codes and its current table size. The ideal outcome is that most lookups have only a few candidates to inspect. A useful hash distributes common unequal keys across many such regions.

### HOW A BUCKET LOOKUP WORKS

Imagine a small teaching table with four buckets. For illustration only, let its index be `Math.floorMod(hash, 4)`:

```text
hash code 5  → bucket 1
hash code 9  → bucket 1   (different hashes, same bucket)
hash code 6  → bucket 2
```

The table has fewer buckets than possible `int` hashes, so even *different* hash codes may lead to the same bucket. This is a **bucket collision**, distinct from two unequal keys returning exactly the same hash code. Real Java 21 `HashMap` may mix hash bits, resize its table and use optimized collision structures; the `floorMod` calculation above is a mental model, **not** its promised indexing algorithm.

For a non-null requested key, the conceptual lookup is:

```text
request a key, such as SeatKey(202)
→ calculate its hash code
→ identify a candidate bucket in the current table
→ compare candidate keys using hash/equality checks as needed
→ return the value for an equal key, or report no match
```

The collection may have to inspect several candidates in that bucket, but it need not test every key in every bucket. `HashMap` also permits one `null` key and handles it specially, so the non-null-key trace does not describe every possible input.

### COLLISION DOES NOT MEAN EQUALITY

The following example deliberately returns the same hash for **every** key. Save it as `CollisionDemo.java` and run it to see why a shared hash must not mean a shared logical value:

```java
import java.util.HashMap;
import java.util.Map;

public class CollisionDemo {
    static final class SeatKey {
        private final int seatNumber;

        SeatKey(int seatNumber) {
            this.seatNumber = seatNumber;
        }

        @Override
        public int hashCode() {
            return 1; // valid contract, deliberately poor distribution
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SeatKey that
                    && seatNumber == that.seatNumber;
        }
    }

    public static void main(String[] args) {
        Map<SeatKey, String> seats = new HashMap<>();
        seats.put(new SeatKey(101), "Window");
        seats.put(new SeatKey(202), "Aisle");

        System.out.println(seats.size());                    // 2
        System.out.println(seats.get(new SeatKey(202)));      // Aisle
        System.out.println(seats.get(new SeatKey(999)));      // null
    }
}
```

Both stored keys have hash `1`, but `SeatKey(101).equals(SeatKey(202))` is false. The map therefore retains **two** entries. When requesting a *new* `SeatKey(202)`, the matching hash leads to the same candidate region; equality rejects other seat numbers and accepts `202`, returning `"Aisle"`. Key `999` has the same hash but no equal stored key, so `get` returns `null`. This example uses only non-null map values, making `null` unambiguous here.

Returning a constant hash is **legal but inefficient**: as more unequal keys accumulate, they compete for the same region and require more candidate work. A useful hash distinguishes many common unequal values; even so, it cannot guarantee a unique integer for every object. There are only 2³² possible `int` results, so sufficiently many distinct logical keys must collide.

### PERFORMANCE BOUNDS AND PRACTICAL CHOICES

Java's `HashMap` documentation describes approximately constant-time basic `get`/`put` operations **assuming the hash function distributes keys properly**. That assumption matters: repeatedly mapping keys to the same bucket increases candidate comparison work, so a fixed O(1) lookup cost is not guaranteed. Java 21 implementations can optimize heavily populated buckets, including using tree-based structures in some cases; their precise layout and collision strategy are implementation details rather than rules your key class may depend on.

The table also has a finite capacity and a load factor: as the map grows, it can expand/reorganize buckets, changing which bucket an unchanged hash reaches. A good hash, reasonable table sizing and stable key state support efficient lookup, while `equals` still decides correctness. The next chapter examines contract violations and key mutation in detail.

### A HASH CODE IS NOT AN ID OR SECURITY TOKEN

Do not infer any of these from a hash alone:

```text
same hash code → objects are equal?       no
hashCode → secure fingerprint/token?     no
hashCode → persistent identity?          no
```

Reference identity and hashing answer different questions:

```text
reference identity (`==`)
→ are these references pointing to the exact same object?

hashCode()
→ input used by a hash-based collection to narrow where it must search
```

Two distinct objects may have the same hash code, while two distinct but logically equal objects **must** have the same hash code. Therefore, a hash cannot establish identity, equality or security; it is a lookup aid operating under the equality contract.

### References

- [Java SE 21 — `Object.hashCode()` contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#hashCode())
- [Java SE 21 — `HashMap` performance and bucket behavior](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html)
- [Java SE 21 — `Objects.hash`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Objects.html#hash(java.lang.Object...))

The next chapter combines `equals` and `hashCode` into **one operational contract** and shows what happens when their guarantees are broken.
