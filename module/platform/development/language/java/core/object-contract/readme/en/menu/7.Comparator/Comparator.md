# Comparator

`Comparable` places one natural order inside a type. `Comparator<T>` moves ordering policy **outside the type**, allowing the same data to be viewed through many orderings.

## <a id="external-order">Custom Ordering</a>

The same `Book` values may be sorted by title, publication date, price, priority, or a compound business key.

The domain class should not need to change for every view. `Comparator` lets callers define the order where it is needed.

## <a id="comparator-composition">Comparator Composition</a>

Java provides factory/composition helpers:

```java
Comparator.comparing(Book::title)
          .thenComparing(Book::publishedAt)
          .reversed();
```

along with primitive-specialized comparators, `thenComparing`, `reversed`, `nullsFirst`, and `nullsLast`.

Tie-breakers should be intentional, especially in `TreeSet`/`TreeMap`, where `compare(...) == 0` may collapse distinct values into one ordering key.

## <a id="comparator-contract">The Comparator Contract</a>

A comparator must define a coherent ordering.

Non-transitive comparison or comparison based on mutable state can produce unpredictable sorting behavior or break ordered collections.

If a field participates in the comparator, avoid mutating it while the object is stored in an ordered structure.

## <a id="sorting-stability-boundary">Sorting Stability</a>

A stable sort preserves the encounter order of elements that compare as equal.

Keep these concepts separate:

```text
sort stability
→ property of the sorting algorithm/API

comparator consistency
→ property of the ordering function
```

Do not assume stability unless the API contract documents it.

After this module, remember that `equals`, `hashCode`, `Comparable`, and `Comparator` are not just methods an IDE can generate. They are **contracts that Java collections and algorithms trust**.
