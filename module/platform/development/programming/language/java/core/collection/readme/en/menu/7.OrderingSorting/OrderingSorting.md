# Ordering and Sorting

A collection often has to answer more than “which elements are present?” It may also need to answer “in what order are those elements observed?”. That order can come from the data structure, from the elements' natural ordering, or from a comparison rule supplied by the caller. These sources are related, but they are not the same thing.

The examples below reuse a small `Order` dataset:

```java
record Order(long id, long userId, int totalCents) {}

List<Order> orders = new ArrayList<>(List.of(
        new Order(3, 2, 10_000),
        new Order(1, 1, 20_000),
        new Order(2, 3, 10_000)
));
```

## <a id="encounter-order">Encounter Order</a>

**Encounter order** is the order in which a sequential traversal sees elements. It describes how elements are encountered; it does not automatically mean “insertion order” or “sorted order”.

Examples:

- `ArrayList` encounters elements in index order.
- `LinkedHashSet` maintains an encounter order based on insertion order.
- `TreeSet` encounters elements in the set's sorted order.
- `HashSet` does not promise a specific encounter order, so application logic should not depend on whatever order happens to be observed.

Java 21 makes encounter order more explicit through `SequencedCollection`, `SequencedSet`, and `SequencedMap`. For example, `List` is a `SequencedCollection` and `SortedSet` is a `SequencedSet`. These abstractions expose first/last operations and reverse-ordered views through `reversed()` where the concrete type supports them.

```java
List<Order> original = new ArrayList<>(orders);
List<Order> reversed = original.reversed();

System.out.println(reversed.getFirst()); // the last element of original
```

`reversed()` returns a reverse-ordered view, not an independent copy. Structural changes made through one side can be observed from the other when the underlying collection supports mutation. If an API boundary needs an independent snapshot, use a copy rather than only a view.

The key idea is that **encounter order is a property of the collection or view during traversal**. A `List` has a defined encounter order without necessarily being sorted, while a `HashSet` may print in the same order several times without that order becoming part of its contract.

## <a id="natural-ordering">Natural Ordering</a>

A type has a **natural ordering** when the type itself defines how its instances are compared, usually through `Comparable<T>` and `compareTo`. A natural ordering should normally represent a stable, intrinsic ordering for the type rather than a temporary sort criterion for one screen or report.

For example, if `Order` uses `id` as its natural order:

```java
record Order(long id, long userId, int totalCents)
        implements Comparable<Order> {

    @Override
    public int compareTo(Order other) {
        return Long.compare(this.id, other.id);
    }
}

List<Order> orders = new ArrayList<>(List.of(
        new Order(3, 2, 10_000),
        new Order(1, 1, 20_000),
        new Order(2, 3, 10_000)
));

orders.sort(null); // null => use natural ordering
```

Natural ordering is also the default for structures such as `TreeSet`, `TreeMap`, and `PriorityQueue` when no explicit `Comparator` is supplied.

As a practical rule, `compareTo` should define a consistent and transitive relation. In sorted sets and maps, a natural ordering that is inconsistent with `equals` can make the collection's idea of “the same element/key” differ from equality-based intuition. The collection consequence is covered in the Equality/Hashing chapter; the `equals/hashCode` contract itself belongs to the `object-contract` module.

## <a id="comparison-result-contract">What Negative, Zero, and Positive Comparison Results Mean</a>

`Comparable.compareTo` and `Comparator.compare` do not have to return exactly `-1`, `0`, or `1`. Their contract depends on the **sign** of the result:

```text
compare(a, b) < 0
→ a comes before b in the ordering

compare(a, b) == 0
→ a and b are equal under the ordering

compare(a, b) > 0
→ a comes after b in the ordering
```

For example:

```java
int result = Long.compare(orderA.id(), orderB.id());
```

If `result < 0`, `orderA` comes before `orderB` by id. Callers should not test `result == -1` or `result == 1`, because an implementation may return any negative or positive value as long as the sign is correct.

When an ordering is used by `TreeSet` or `TreeMap`, a result of `0` also affects element/key uniqueness. “Equal under the ordering” therefore has a real collection consequence, not only a sorting consequence.

## <a id="comparator-ordering">Comparator-Based Ordering</a>

`Comparator<T>` places the ordering rule **outside** the data type. This is useful when the same object needs multiple meaningful orderings: by total, by user, by time, by priority, and so on.

```java
Comparator<Order> byTotalDescendingThenId =
        Comparator.comparingInt(Order::totalCents)
                .reversed()
                .thenComparingLong(Order::id);

orders.sort(byTotalDescendingThenId);
```

Adding a tie-breaker such as `thenComparingLong(Order::id)` produces a clearer total order when the main criterion is equal. The same comparator shape can be supplied to `List.sort`, `TreeSet`, `TreeMap`, or `PriorityQueue`, but its meaning depends on the data structure:

- With `List.sort`, the comparator determines the resulting order after the sort operation.
- With `TreeSet`, a comparison result of `0` also means the two elements occupy the same logical position for set uniqueness.
- With `TreeMap`, the comparator determines both key order and key uniqueness under the comparison relation.
- With `PriorityQueue`, the comparator determines which element is at the head; iterating the queue does not therefore return every element in fully sorted order.

A comparator is therefore more than “a function used for sorting”. When placed into a sorted collection, it becomes part of that collection's organization and identity boundary.

## <a id="navigable-range-operations">Navigation and Range Views After Ordering Is Understood</a>

Once the learner understands `Set`, `Map`, and ordering, the `NavigableSet`/`NavigableMap` APIs become much easier to reason about.

```java
NavigableSet<Integer> ids = new TreeSet<>(List.of(10, 20, 30, 40));

System.out.println(ids.lower(20));   // 10: < 20
System.out.println(ids.floor(25));   // 20: <= 25
System.out.println(ids.ceiling(25)); // 30: >= 25
System.out.println(ids.higher(30));  // 40: > 30
```

Range views expose a portion of sorted data without copying the whole structure:

```java
NavigableSet<Integer> middle = ids.subSet(20, true, 40, false); // [20, 30]
NavigableSet<Integer> head = ids.headSet(30, true);              // <= 30
NavigableSet<Integer> tail = ids.tailSet(20, false);             // > 20
```

These results are **backed views**. Supported mutation through the view affects the source, and source changes may be observed through the view according to its contract.

The `Map` branch applies the same mental model to **keys**:

```java
NavigableMap<Long, Order> byId = new TreeMap<>();
byId.put(10L, orderA);
byId.put(20L, orderB);
byId.put(30L, orderC);

Map.Entry<Long, Order> floor = byId.floorEntry(25L); // key 20
NavigableMap<Long, Order> firstTwo =
        byId.subMap(10L, true, 30L, false);           // keys 10, 20
```

`lower/floor/ceiling/higher` answer “which neighboring element/key is closest on this side?”, while `subSet/subMap/head*/tail*` create range views. This is why sorted/navigable structures are useful when the problem needs order-based navigation, not merely output that “looks sorted”.

## <a id="stable-sort">Stable Sort and Tie Behavior</a>

A sort is **stable** when elements that compare as equal retain their original relative order. `List.sort` and `Collections.sort` are specified to be stable.

```java
List<Order> orders = new ArrayList<>(List.of(
        new Order(3, 2, 10_000),
        new Order(1, 1, 20_000),
        new Order(2, 3, 10_000)
));

orders.sort(Comparator.comparingInt(Order::totalCents));

// Orders 3 and 2 both have totalCents = 10_000.
// Order 3 remains before order 2 because the sort is stable.
```

Stable sorting is useful when the input already has a meaningful secondary order. For example, data can first be ordered by time and then stable-sorted by status so that time order remains intact inside each status group.

Do not confuse a stable sort with a sorted collection. `TreeSet` does not keep two comparison-equal elements and remember their previous relative order; if the comparator returns `0`, the set treats them as equivalent for ordering and retains only one representative. If the business needs both, use an appropriate tie-breaker or choose a different structure.

## <a id="collections-utility-algorithms">Utility Algorithms in Collections</a>

`Collections` is a **utility class**, not the `Collection<E>` interface. It provides static algorithms that operate on existing collections/lists. There is no need to memorize the full API; a representative set shows the purpose of this layer:

```java
List<Integer> values = new ArrayList<>(List.of(30, 10, 20));

Collections.sort(values);          // [10, 20, 30]
Collections.reverse(values);       // [30, 20, 10]
Collections.shuffle(values);       // randomizes order

int min = Collections.min(values);
int max = Collections.max(values);
```

In modern code, `list.sort(comparator)` is often more direct than `Collections.sort(list, comparator)`, but both illustrate the same idea: **an algorithm can be separated from the concrete list implementation**.

`binarySearch` has an important precondition: the list must already be sorted using the **same ordering** used by the search.

```java
List<Integer> sorted = new ArrayList<>(List.of(10, 20, 30, 40));
int index = Collections.binarySearch(sorted, 30); // 2
```

If the list is not sorted, or the search ordering differs from the sort ordering, the result is not meaningful. When no match is found, the method returns a negative value encoding the insertion point; callers should not treat every negative result as simply “index -1”.

Utilities such as `reverse`, `shuffle`, and `sort` mutate the list when mutation is supported. Passing an unmodifiable list such as `List.of(...)` to a mutating utility can produce `UnsupportedOperationException`. Always consider **both the algorithm contract and the input collection's mutability contract**.
