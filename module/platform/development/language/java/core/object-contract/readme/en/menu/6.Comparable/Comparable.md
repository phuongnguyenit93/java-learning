# Comparable

If a type has **one clear natural ordering**, the type itself can publish that ordering through `Comparable<T>`.

`Comparable` is not only about `Collections.sort`. Its contract also affects binary-search-style algorithms, `TreeSet`, `TreeMap`, and other APIs that rely on natural ordering.

## <a id="natural-order">Natural Ordering</a>

`Comparable<T>` requires:

```java
int compareTo(T other);
```

`Comparable` publishes a **total order** over valid comparable values of the type: for two comparable values, the ordering must place one before the other or place them in the same ordering equivalence class. A natural order should be a default ordering users of the type can reasonably predict.

For example, a `Version` can naturally order by:

```text
major
  ↓ if equal
minor
  ↓ if equal
patch
```

```java
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
```

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

Callers should not depend on exact values such as `-1`, `0`, or `1`.

### SIGN ANTISYMMETRY

If:

```text
sign(a.compareTo(b)) < 0
```

then the reverse comparison must have the opposite sign:

```text
sign(b.compareTo(a)) > 0
```

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

Without transitivity, sorting algorithms and tree-based structures do not have one coherent order to work with.

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

Otherwise two values declared equivalent in the ordering would behave differently against a third value, so the ordering would not be coherent.

### DO NOT COMPARE BY SUBTRACTION

Avoid:

```java
return this.id - other.id;
```

because integer overflow can reverse the sign.

```java
int a = Integer.MAX_VALUE;
int b = -1;

int result = a - b; // overflow
```

Prefer:

```java
Integer.compare(this.id, other.id)
Long.compare(this.id, other.id)
```

or comparator helpers that express the intent safely.

### `compareTo(null)`

The `Comparable` contract requires `x.compareTo(null)` to throw `NullPointerException` even though `x.equals(null)` returns `false`. Natural ordering does not use `null` as a valid peer value. A policy such as “nulls first” or “nulls last” belongs more naturally to `Comparator.nullsFirst` or `Comparator.nullsLast`.

## <a id="compareto-equals-consistency">Consistency with equals</a>

When a natural ordering is **consistent with `equals`**, these two boolean expressions agree:

```text
(compareTo(other) == 0)
        ↕ equivalent
equals(other)
```

Java strongly recommends this consistency, although it is not mandatory. The reason is operational, not merely stylistic: sorted collections use comparison to identify ordering keys, while the general `Set`/`Map` contracts are defined in terms of `equals`.

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

a `HashSet` may keep both values while a `TreeSet` may treat them as one ordering key.

The reverse mismatch is surprising too:

```text
a.equals(b) == true
a.compareTo(b) != 0
```

then a sorted set may retain both values even though logical equality says they are equal, causing sorted-collection behavior to diverge from normal `Set` expectations.

`BigDecimal` is a well-known example:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));     // false
System.out.println(a.compareTo(b));  // 0
```

That is legal because the type documents its semantics, but callers must understand the consequence when choosing a collection.

Consistency with `equals` is **strongly recommended rather than mandatory** for `Comparable`. If a class intentionally defines a natural ordering that is inconsistent with `equals`, that behavior should be documented clearly because sorted collections use ordering semantics rather than logical equality to determine duplicate keys.

### MUTABLE ORDERING STATE

Like a mutable hash key, changing fields that participate in ordering while an object is stored in `TreeSet`/`TreeMap` can make the tree placement inconsistent with the object's new state.

Prefer stable ordering-relevant state for objects used as sorted keys.

When a type needs multiple orderings, `Comparator` is the better tool.
