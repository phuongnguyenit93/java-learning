# Putting the Object Contracts Together

## <a id="contract-synthesis">Putting the Object Contracts Together</a>

Earlier chapters established the individual contracts. Now imagine passing the **same values** to several Java APIs: one API might keep two of them, while another keeps only one. To predict the result, ask **which question that API uses to decide whether two objects count as the same**. This is a design decision, not just a matter of generating methods in an IDE.

### One object, several contracts

Use this model when you read or design a class:

| Operation | Question it answers | Where the answer matters |
| --- | --- | --- |
| `a == b` for references | Do both variables refer to the **same instance**? | Identity checks; it does not compare a class's logical values. |
| `a.equals(b)` | Do these instances represent the **same logical value or entity**? | Value-oriented APIs such as `List.contains`; equality checks in hash-based collections. |
| `a.hashCode()` | Which hash value can a hash-based structure use to find candidates? | `HashSet` and `HashMap`, together with `equals`. Equal objects **must** have equal hash codes; different objects may collide. |
| `a.toString()` | What text represents this object for readers and tools? | Logs, debugging, and diagnostic messages; ordinary `toString` output is not a persistence format contract. |
| `a.compareTo(b)` (`Comparable`) | Which comes before, after, or at the same position in the type's **natural order**? | Natural sorting and naturally ordered `TreeSet`/`TreeMap`. |
| `comparator.compare(a, b)` (`Comparator`) | What ordering does this **caller-selected policy** impose? | Alternative sorting and sorted collections constructed with that comparator. |

These answers are related but not interchangeable. For example, `equals` is a yes/no logical equality decision; `compareTo` and `Comparator.compare` return a negative value, zero, or a positive value to express ordering. **Zero means equivalent for that ordering**, which does not necessarily mean `equals` returns `true`.

### Which Java API observes which contract?

Start with a lookup or insertion, then trace the decision the collection makes:

1. A `HashSet`/`HashMap` uses `hashCode` to locate possible matches and `equals` to decide equality among relevant candidates. The hash code is not an identity number and does not by itself establish equality.
2. A `TreeSet`/`TreeMap` uses `compareTo` or the supplied `Comparator` to navigate its order. When comparison returns **zero**, the sorted structure treats the two values as the same set element or map key, even if their `equals` results differ.
3. Sorting a `List` with `Comparable` or `Comparator` changes **the order** of its elements; it does **not remove duplicates** or redefine `equals`.
4. Logs and diagnostic output can call `toString`. Readable text helps explain a value, but matching text does not prove that two objects are equal.

For a sorted set or map to honor the general `Set`/`Map` equality contract, the ordering should be **consistent with `equals`**: for every relevant pair, comparison returns zero exactly when `equals` is `true`. Java permits types such as `BigDecimal` whose natural order differs from `equals`; choosing a collection then has a visible semantic consequence.

### Worked experiment: same decimal value, different equality

`BigDecimal` makes the distinction concrete without needing to invent a broken class. Its `equals` compares both numeric value and **scale** (roughly, how many decimal places are stored), while `compareTo` compares numeric value. Thus `1.0` and `1.00` are equal in the natural order but **not equal under `equals`**. The Numbers module explains the full value-and-scale model; here we use just enough of it to observe the object contracts.

Save the following complete Java 21 program as `ContractSynthesisDemo.java`. Predict the final set sizes before running `javac --release 21 ContractSynthesisDemo.java` and `java ContractSynthesisDemo`:

```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class ContractSynthesisDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");
        BigDecimal c = new BigDecimal("1.0");

        System.out.println("a == c: " + (a == c));
        System.out.println("a.equals(c): " + a.equals(c));
        System.out.println("equal hash codes (a,c): " + (a.hashCode() == c.hashCode()));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("a.compareTo(b): " + a.compareTo(b));
        System.out.println("a.toString(): " + a.toString());
        System.out.println("b.toString(): " + b.toString());

        Set<BigDecimal> hashValues = new HashSet<>();
        hashValues.add(a);
        hashValues.add(b);
        hashValues.add(c);
        System.out.println("HashSet size: " + hashValues.size());
        System.out.println("HashSet contains c: " + hashValues.contains(c));

        Set<BigDecimal> naturalValues = new TreeSet<>();
        naturalValues.add(a);
        System.out.println("TreeSet contains b after only a: " + naturalValues.contains(b));
        naturalValues.add(b);
        naturalValues.add(c);
        System.out.println("natural TreeSet size: " + naturalValues.size());

        Comparator<BigDecimal> valueThenScale =
                Comparator.<BigDecimal>naturalOrder().thenComparingInt(BigDecimal::scale);
        Set<BigDecimal> scaleAwareValues = new TreeSet<>(valueThenScale);
        scaleAwareValues.add(a);
        scaleAwareValues.add(b);
        scaleAwareValues.add(c);
        System.out.println("value-then-scale TreeSet size: " + scaleAwareValues.size());
    }
}
```

Expected output:

```text
a == c: false
a.equals(c): true
equal hash codes (a,c): true
a.equals(b): false
a.compareTo(b): 0
a.toString(): 1.0
b.toString(): 1.00
HashSet size: 2
HashSet contains c: true
TreeSet contains b after only a: true
natural TreeSet size: 1
value-then-scale TreeSet size: 2
```

Follow the causes, not just the numbers:

- `a` and `c` are separate `new` objects (`==` is `false`), yet represent the same value and scale (`equals` is `true`), so their hash codes agree. This demonstrates the **equal objects → equal hash codes** rule; unequal objects are *not* required to have distinct hashes.
- `a` and `b` differ in scale, so `equals` rejects the pair. `HashSet` retains both; `c` duplicates `a` under `equals`, so the final size is **2**. Looking up `c` succeeds even when the stored equal instance is `a`.
- After only `a` was inserted into the natural `TreeSet`, `contains(b)` already returns **`true`**: the tree asks `a.compareTo(b)` (up to comparison direction), which is zero. Adding `b` and `c` still leaves **1** ordering-equivalent element. This behavior is defined for `TreeSet`, but with `BigDecimal`'s natural ordering it does not follow the ordinary `Set` notion of equality based on `equals`.
- `valueThenScale` first compares numeric value; if those are tied, it compares scale. Now `1.0` and `1.00` have different positions, while `a` and `c` remain equivalent. The comparator-based tree therefore keeps **2** elements. For these `BigDecimal` values, this ordering is consistent with `equals`.
- The `toString` lines show diagnostic representations, not a second equality rule. `BigDecimal` specifically documents a canonical `toString`; do not assume arbitrary domain classes provide a stable serialized format through `toString`.

### Stable state is a cross-cutting requirement

The example uses immutable `BigDecimal` instances. With a mutable domain class, the same contracts must keep working **throughout the object's membership in a collection**. Consider a `Customer` object whose `id` participates in `equals`, `hashCode`, and natural ordering:

1. Put the customer in a `HashSet` while `id` is `"C-7"`. Hash lookup uses the hash calculated from that value.
2. Change the same object's `id` to `"C-8"` without removing it. A later `contains(customer)` now hashes `"C-8"` and **may look in a different location**, failing to find even that same instance. A matching `HashMap` key has the analogous risk.
3. If the customer is instead in a `TreeSet` ordered by `id`, changing `id` does not automatically relocate the existing tree node. Its position can now disagree with comparisons used for later lookup or insertion.

An external `Comparator` that reads mutable ranking data can cause the same tree inconsistency even if the objects themselves do not change. Immutability is not mandatory, but **equality-, hashing-, and ordering-relevant values must remain stable while a collection relies on them**. Prefer immutable keys/order fields, or explicitly remove an entry before changing relevant state and insert it again under the new state when that operation is safe for the collection and domain. Changing only a field ignored by all these contracts does not cause this particular problem.

### Verification exercise: predict, run, and justify

Use the runnable program above; no testing library or additional module is required.

1. **Baseline:** Before running, write down the results of `a == c`, `a.equals(c)`, `a.equals(b)`, `a.compareTo(b)`, and the three set sizes. Run it and explain which comparison each collection used.
2. **Change one input:** Replace only `new BigDecimal("1.00")` for `b` with `new BigDecimal("1.0")`. Predict what happens to `a.equals(b)` and all three sizes. Compile and run again to check.
3. **Change one ordering policy:** Restore `b` to `"1.00"`, then replace the comparator expression with `Comparator.reverseOrder()`. Predict the final comparator-based tree size. Explain why reversing numeric order does not distinguish two numerically equal values.
4. **Choose an API:** An application must keep both the `1.0` and `1.00` representations as distinct set members. Which of the three constructions fits? If it only needs to *display* the two values in an order without eliminating either one, would sorting a `List` be simpler?
5. **Debug a different class:** A mutable customer ID determines `hashCode` and ordering. After insertion, the ID changes. Name one potential `HashSet` failure, one potential `TreeSet` failure, and a safe design choice.

**Check your reasoning:** Step 2 changes `a.equals(b)` to `true` and all three set sizes to **1**, because `a`, `b`, and `c` now agree in both value and scale. Step 3 leaves the comparator-based `TreeSet` size at **1**: reverse ordering still compares `1.0` and `1.00` as zero. For Step 4, a `HashSet` or a `TreeSet` using `valueThenScale` retains both representations, while a natural-order `TreeSet` collapses them; sorting a `List` retains both entries. For Step 5, hash lookup can fail after the relevant hash changes, and a sorted tree may retain an outdated placement. Keep the ID stable while the object is stored, or use a separate immutable key.

### Design checklist

Before using a domain type as a map key, set element, sortable value, or diagnostic object, ask:

1. What defines **logical equality**, and how does it differ from reference identity (`==`)?
2. Does `hashCode` use state consistent with that equality definition? Remember that a matching hash does not prove equality.
3. Can equality- or ordering-relevant state change while the object belongs to a hash-based or sorted collection?
4. Does the type truly have one natural order (`Comparable`), or should a caller select an alternative with `Comparator`?
5. Is the ordering transitive and otherwise internally consistent, and does comparison returning zero agree with `equals` for values used in a sorted set/map?
6. If natural ordering is intentionally inconsistent with `equals`, as for `BigDecimal`, is the consequence understood and documented at the call site?
7. Does `toString` communicate useful diagnostic state without exposing secrets or being mistaken for a general serialization contract?

The goal is to **predict the Java API's behavior from the contract it observes**. That is what connects identity, logical equality, hashing, representation, ordering, and stable state into one reliable object design.

### References

- [Oracle Java SE 21 — `Object` (`equals`, `hashCode`, `toString`)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html)
- [Oracle Java SE 21 — `BigDecimal` (equality, scale, natural order)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [Oracle Java SE 21 — `HashSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html)
- [Oracle Java SE 21 — `TreeSet` (ordering consistency and the `Set` contract)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html)
- [Oracle Java SE 21 — `Comparator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html)
