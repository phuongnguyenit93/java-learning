# Comparable

If a type has **one clear natural ordering**, the type itself can publish that ordering through `Comparable<T>`.

## <a id="natural-order">Natural Ordering</a>

`Comparable<T>` requires:

```java
int compareTo(T other);
```

A natural order should be a default sort order users of the type can reasonably predict.

For example, a `Version` type may naturally order by major/minor/patch. If several orderings are equally valid, an external `Comparator` is usually a better design.

## <a id="compareto-contract">The compareTo Contract</a>

The sign of `compareTo` means:

```text
< 0  → this comes before other
  0  → same position in the ordering
> 0  → this comes after other
```

The ordering should preserve sign antisymmetry, transitivity, and consistency required by sorting/search structures.

Avoid:

```java
return this.id - other.id;
```

because subtraction can overflow. Prefer `Integer.compare`, `Long.compare`, or comparator helpers.

## <a id="compareto-equals-consistency">Consistency with equals</a>

It is strongly recommended that:

```text
compareTo(other) == 0
→ agrees with equals(other) == true
```

Otherwise `TreeSet` or `TreeMap` may treat two objects that `equals` considers different as the same ordering key.

`BigDecimal` is a famous deliberate exception, which is why callers must understand the collection semantics involved.

When a type needs multiple orderings, `Comparator` is the better tool.
