# Natural Ordering with Comparable

If a type has **one clear natural ordering**, the type itself can publish that ordering through `Comparable<T>`.

`Comparable` is not only about `Collections.sort`. Its contract also affects binary-search-style algorithms, `TreeSet`, `TreeMap`, and other APIs that rely on natural ordering.

## <a id="natural-order">Natural Ordering</a>

`Comparable<T>` requires:

```java
int compareTo(T other);
```

`Comparable` publishes a **total order** over values of the type that it can compare: for any two such values, one comes before the other, or both belong to the same *ordering equivalence class* (`compareTo` returns `0`). Being equivalent in an ordering does not necessarily mean being equal according to `equals`; the last section shows why this distinction matters. A natural order should be a default ordering users of the type can reasonably predict.

For example, a `Version` can naturally order by:

```text
major
  ↓ if equal
minor
  ↓ if equal
patch
```

Here is a complete Java 21 example. Save it as `ComparableVersionDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

record Version(int major, int minor, int patch)
        implements Comparable<Version> {

    @Override
    public int compareTo(Version other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) return byMajor;

        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) return byMinor;

        return Integer.compare(patch, other.patch);
    }
}

public class ComparableVersionDemo {
    public static void main(String[] args) {
        Version a = new Version(1, 9, 4);
        Version b = new Version(1, 10, 0);
        Version c = new Version(2, 0, 0);

        System.out.println(Integer.signum(a.compareTo(b)));
        System.out.println(Integer.signum(b.compareTo(a)));
        System.out.println(Integer.signum(b.compareTo(new Version(1, 10, 0))));

        List<Version> releases = new ArrayList<>(List.of(c, b, a));
        Collections.sort(releases); // Uses Version.compareTo
        System.out.println(releases);
    }
}
```

Output:

```text
-1
1
0
[Version[major=1, minor=9, patch=4], Version[major=1, minor=10, patch=0], Version[major=2, minor=0, patch=0]]
```

Trace the comparisons rather than memorizing the output:

| Compared values | First field that differs | Result |
| --- | --- | --- |
| `1.9.4` versus `1.10.0` | `major` ties at `1`; `minor` is `9 < 10` | negative: `1.9.4` comes first |
| `1.10.0` versus `1.9.4` | `minor` is `10 > 9` | positive: reverse order |
| `1.10.0` versus another `1.10.0` | No field differs | zero: tied in the natural order |
| `1.10.0` versus `2.0.0` | `major` is `1 < 2` | negative: no need to check the remaining fields |

`Collections.sort` repeatedly asks `compareTo` which element should come first, then rearranges the list accordingly. Starting with `[2.0.0, 1.10.0, 1.9.4]` produces `[1.9.4, 1.10.0, 2.0.0]`. Because the record's generated `equals` also checks exactly these three fields, this particular natural ordering is consistent with `equals`.

### WHEN SHOULD A TYPE NOT HAVE A NATURAL ORDER?

If several orderings are equally meaningful, choosing one arbitrarily can surprise users.

A `Book` may be ordered by title, publication date, price, or rating. None is necessarily the one intrinsic order of the type. In that case, external `Comparator` policies are often better.

## <a id="compareto-contract">The compareTo Contract</a>

Only the **sign** of the result matters:

```text
< 0  → this comes before other
  0  → same position in the ordering
> 0  → this comes after other
```

Callers should not depend on exact negative or positive values such as `-1` or `1`. The demo uses `Integer.signum` to normalize each result to `-1`, `0`, or `1` for display.

### SIGN ANTISYMMETRY

If:

```text
sign(a.compareTo(b)) < 0
```

then the reverse comparison must have the opposite sign:

```text
sign(b.compareTo(a)) > 0
```

In the `Version` example, `1.9.4` compared with `1.10.0` is negative; swapping the arguments makes it positive. More generally, the Java contract requires `signum(a.compareTo(b)) == -signum(b.compareTo(a))`, including the case where both results are zero. If one direction throws an exception, the reverse direction must also throw.

### TRANSITIVITY

If:

```text
a < b
b < c
```

the ordering must also preserve:

```text
a < c
```

For `a = 1.9.4`, `b = 1.10.0`, and `c = 2.0.0`, comparing `a` with `b` first finds `9 < 10` in the minor field. Comparing `b` with `c` finds `1 < 2` in the major field. Comparing `a` directly with `c` also finds `1 < 2` in the major field, so `a < c` as required. Without transitivity, sorting algorithms and tree-based structures do not have one coherent order to work with.

### ORDERING CONSISTENCY

If ordering-relevant state is unchanged, comparison should not vary randomly with time or external state.

The contract also has a subtler requirement: if `a.compareTo(b) == 0`, then `a` and `b` must have the same ordering relationship to a third value `c`.

```text
a.compareTo(b) == 0
        ↓
sign(a.compareTo(c))
must match
sign(b.compareTo(c))
```

For example, let `x` and `y` be two separately created `Version(1, 10, 0)` objects, and let `z` be `Version(2, 0, 0)`. Since `x.compareTo(y) == 0`, both `x.compareTo(z)` and `y.compareTo(z)` must have the same negative sign. Otherwise two values declared equivalent in the ordering would behave differently against a third value, so the ordering would not be coherent.

### DO NOT COMPARE BY SUBTRACTION

Avoid:

```java
return this.id - other.id;
```

because integer overflow can reverse the sign.

Inside a method, the error is observable:

```java
int a = Integer.MAX_VALUE;
int b = -1;

System.out.println(a - b);                 // -2147483648 (overflow)
System.out.println(Integer.compare(a, b)); // 1 (correct: a > b)
```

Mathematically, `2147483647 - (-1)` is `2147483648`, but that number cannot fit in a Java `int`. Integer arithmetic wraps, producing a negative number and therefore the **wrong ordering sign**. `Integer.compare` avoids that subtraction.

Prefer:

```java
Integer.compare(this.id, other.id)
Long.compare(this.id, other.id)
```

or comparator helpers that express the intent safely.

### `compareTo(null)`

The `Comparable` contract requires `x.compareTo(null)` to throw `NullPointerException` even though `x.equals(null)` returns `false`. In the `Version` example, accessing `other.major` when `other` is `null` throws that exception. Natural ordering does not use `null` as a valid peer value. A policy such as “nulls first” or “nulls last” belongs more naturally to `Comparator.nullsFirst` or `Comparator.nullsLast`.

## <a id="compareto-equals-consistency">Consistency with equals</a>

When a natural ordering is **consistent with `equals`**, these two boolean expressions agree:

```text
(compareTo(other) == 0)
        ↕ equivalent
equals(other)
```

Java strongly recommends this consistency, although `Comparable` itself does not require `compareTo(...) == 0` to imply `equals(...) == true`. The reason is operational, not merely stylistic: sorted collections use comparison to identify ordering keys, while the general `Set`/`Map` contracts are defined in terms of `equals`.

### HASH-BASED AND TREE-BASED COLLECTIONS INTERPRET “SAME” DIFFERENTLY

Mental model:

```text
HashSet / HashMap
→ equals + hashCode

TreeSet / TreeMap
→ compareTo or Comparator
```

If:

```text
a.equals(b) == false
a.compareTo(b) == 0
```

a `HashSet` can keep both values, while a naturally ordered `TreeSet` treats them as one ordering key when the comparison returns zero. A naturally ordered `TreeMap` similarly considers the two values the same key and may replace the earlier associated value.

The reverse mismatch is surprising too:

```text
a.equals(b) == true
a.compareTo(b) != 0
```

then a sorted set may retain both values even though logical equality says they are equal, causing sorted-collection behavior to diverge from normal `Set` expectations.

`BigDecimal` is a well-known example. Both numbers represent the same numerical amount, but `1.0` has scale `1` and `1.00` has scale `2`. `compareTo` compares numerical value; `equals` also considers scale. Save this complete Java 21 program as `BigDecimalOrderDemo.java`:

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class BigDecimalOrderDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");

        System.out.println(a.equals(b));    // false
        System.out.println(a.compareTo(b)); // 0

        Set<BigDecimal> hashed = new HashSet<>();
        hashed.add(a);
        hashed.add(b);
        System.out.println(hashed.size());  // 2

        Set<BigDecimal> sorted = new TreeSet<>();
        sorted.add(a);
        sorted.add(b);
        System.out.println(sorted.size());  // 1

        TreeMap<BigDecimal, String> map = new TreeMap<>();
        map.put(a, "original");
        map.put(b, "replacement");
        System.out.println(map.size());     // 1
        System.out.println(map.get(a));     // replacement
    }
}
```

Output:

```text
false
0
2
1
1
replacement
```

Follow the operations: `HashSet.add(b)` sees a different value according to `equals` and retains both values, so its size becomes `2`. `TreeSet.add(b)` finds an existing value for which natural comparison returns `0`, so it keeps only one element. `TreeMap.put(b, ...)` likewise finds the ordering key inserted by `a` and **replaces its mapped value**; looking up with `a` now returns `replacement`. Neither sorted collection rewrites `BigDecimal.equals`.

That type-level ordering is allowed, but callers must understand the consequence when choosing a collection.

If a class intentionally defines a natural ordering that is inconsistent with `equals`, document it clearly. `TreeSet`/`TreeMap` behavior is still well-defined because they use ordering to identify keys, but the collection may no longer satisfy the general `Set`/`Map` contract whose notion of equality is expressed through `equals`.

### MUTABLE ORDERING STATE

Like a mutable hash key, changing fields that participate in ordering while an object is stored in `TreeSet`/`TreeMap` can make the tree placement inconsistent with the object's new state. Imagine inserting two mutable keys ordered by IDs `10` and `20`. If the second key's ID changes from `20` to `5`, it remains at the tree position chosen for `20`; iteration and lookups are no longer reliable as though the keys had been inserted in the new order.

Prefer stable ordering-relevant state for objects used as sorted keys.

When a type needs multiple orderings, `Comparator` is the better tool. Its contract is similar, but a caller supplies the ordering policy instead of making it the type's natural order.

### References

- [Java 21 `Comparable` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Comparable.html) — formal `compareTo` rules, including consistency with `equals`.
- [Java 21 `BigDecimal` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html) — why numeric ordering and scale-aware equality differ.
- [Java 21 `TreeSet` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html) and [Java 21 `TreeMap` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeMap.html) — how sorted collections interpret equal ordering keys.
