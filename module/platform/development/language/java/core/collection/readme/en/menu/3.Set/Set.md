# Set

After `List`, the question changes from “which position is this element in?” to “may the same logical value appear more than once?” When the answer is no, `Set` expresses that intent better than a `List` plus duplicate checks scattered through application code.

## <a id="set-semantics">Set Semantics</a>

The general `Set<E>` contract is that it does **not contain two elements that are equal under the set equality contract**. In ordinary hash-based implementations such as `HashSet`, `equals` determines equality while `hashCode` helps locate candidate elements efficiently. The sorted-set nuance of `TreeSet` is deferred to its own section after the learner has the basic Set model.

For example, if we only need to know which users have orders:

```java
Set<Long> userIds = new HashSet<>();
userIds.add(7L);
userIds.add(8L);
userIds.add(7L);

System.out.println(userIds.size()); // 2
```

The second `add(7L)` does not create another element. `Set.add` returns `false` when the set does not change because an equivalent element is already present:

```java
boolean first = userIds.add(9L);  // true
boolean again = userIds.add(9L);  // false
```

A `Set` does not promise index-based access. If code needs “the third element,” a `List` or an explicitly ordered abstraction is usually a better fit.

Null policies vary by implementation. `HashSet` and `LinkedHashSet` allow one null element; a `TreeSet` using ordinary natural ordering does not accept null. Code should rely on the selected implementation's contract rather than assuming every `Set` behaves the same.

Since Java 21, `SequencedSet` combines `Set` uniqueness with a defined first/last encounter order. `LinkedHashSet` and the modern sorted-set hierarchy participate in that abstraction, allowing APIs to state that both uniqueness and order are part of the contract.

## <a id="set-algebra">Set Operations Through the Set API</a>

Because a `Set` models unique members, the bulk operations inherited from `Collection` map naturally to set-style reasoning:

```java
Set<Long> first = new HashSet<>(Set.of(1L, 2L, 3L));
Set<Long> second = Set.of(3L, 4L);
```

### Union

```java
Set<Long> union = new HashSet<>(first);
union.addAll(second); // [1, 2, 3, 4]
```

### Intersection

```java
Set<Long> intersection = new HashSet<>(first);
intersection.retainAll(second); // [3]
```

### Difference

```java
Set<Long> difference = new HashSet<>(first);
difference.removeAll(second); // [1, 2]
```

These operations **mutate the receiving set** when it is mutable. Create a copy first when the original input must remain unchanged, as the examples do.

## <a id="set-equality">Set Equality Ignores Encounter Order</a>

Two sets are equal when they have the same size and every element of one set is contained in the other. **Encounter order does not participate in `Set.equals`.**

```java
Set<Long> a = new HashSet<>(List.of(10L, 20L, 30L));
Set<Long> b = new LinkedHashSet<>(List.of(30L, 20L, 10L));

System.out.println(a.equals(b)); // true
```

That differs from `List.equals`, where each element's position is part of equality. If the domain value is “which members exist” rather than “which member is at each position,” `Set` usually expresses the semantics more accurately.

## <a id="hashset-model">HashSet Model</a>

`HashSet` is a natural choice when membership/uniqueness is the main requirement and no particular encounter order is required. A useful mental model is:

```text
element
  ↓ hashCode()
select candidate bucket/region
  ↓
equals() confirms equivalence
```

With a reasonable hash distribution, `add`, `contains`, and `remove` have expected near-O(1) cost. That is an expected complexity characteristic, not a guarantee that every individual call is constant time.

```java
Set<Long> processedOrderIds = new HashSet<>();

if (processedOrderIds.add(order.id())) {
    process(order);
}
```

Here, `add` both tests and records uniqueness, avoiding an unnecessary “`contains` then `add`” pattern in single-threaded code.

Exact bucket layout, resizing, and tree bins are implementation details and may evolve. The important contract boundary is:

1. an element must preserve stable equality/hash behavior while stored in the set;
2. hashing narrows the candidate search area;
3. `equals` confirms equality for a hash-based collection.

If an object changes a field used by `equals/hashCode` after insertion into a `HashSet`, lookup can become inconsistent from the caller's perspective because the object no longer matches the hash location where it was stored. The Equality/Hashing chapter owns the detailed invariant; the practical rule here is to avoid mutating fields that define logical identity while an element is stored in a hash set.

`HashSet` does not guarantee iteration order. Output that happens to look stable during a few runs is not an ordering contract.

## <a id="linkedhashset-order">LinkedHashSet Order</a>

`LinkedHashSet` adds a defined encounter order to hash-set uniqueness. With ordinary `add` operations, that order is the order in which elements were first inserted.

```java
Set<Long> userIds = new LinkedHashSet<>();
userIds.add(7L);
userIds.add(2L);
userIds.add(9L);
userIds.add(2L);

System.out.println(userIds); // [7, 2, 9]
```

Adding `2L` again does not create a duplicate and does not automatically move it to the end under ordinary `add` semantics.

This implementation is useful when the domain needs both:

```text
uniqueness
    +
predictable encounter order
```

For example, we can collect unique users in the order they first appear in an order stream:

```java
Set<Long> usersInFirstSeenOrder = new LinkedHashSet<>();
for (Order order : orders) {
    usersInFirstSeenOrder.add(order.userId());
}
```

In Java 21, `LinkedHashSet` implements `SequencedSet`. In addition to its encounter order, it gains first/last operations, explicit positioning such as `addFirst`/`addLast`, and a `reversed()` view. That makes its ordering contract directly expressible rather than merely observable when printing values.

This ordering requires extra linkage metadata compared with `HashSet`. Pay that cost when stable order has value to the program.

## <a id="treeset-order">TreeSet Order</a>

`TreeSet` solves a different problem: it maintains elements in **sorted order**, rather than insertion order.

```java
Set<Long> orderIds = new TreeSet<>();
orderIds.add(30L);
orderIds.add(10L);
orderIds.add(20L);

System.out.println(orderIds); // [10, 20, 30]
```

Ordering comes from:

- an element's natural ordering when no comparator is supplied;
- or a `Comparator<? super E>` provided when the set is created.

```java
record User(long id, String name) {}

Set<User> usersByName = new TreeSet<>(
        Comparator.comparing(User::name)
                  .thenComparingLong(User::id)
);
```

For `TreeSet`, a comparison result of zero means two elements occupy the same logical set position:

```java
Comparator<User> byNameOnly = Comparator.comparing(User::name);
Set<User> users = new TreeSet<>(byNameOnly);

users.add(new User(1, "An"));
users.add(new User(2, "An"));

System.out.println(users.size()); // 1
```

The two users have different IDs, but the comparator looks only at `name`, so `compare(a, b) == 0` and `TreeSet` treats them as one logical position for storage. If ordering is inconsistent with `equals`, `TreeSet` still has defined behavior, but the sorted set no longer cleanly implements the general equality contract of `Set`. In practice, a comparator used for `TreeSet` should be consistent with the equality notion the API intends to expose, or a different structure should be chosen.

`TreeSet` typically provides O(log n) `add`, `contains`, and `remove` operations. In exchange, it provides sorted/navigable operations such as `first`, `last`, `lower`, `higher`, `floor`, and `ceiling`.

Since Java 21, the sorted-set hierarchy also has sequenced semantics, so first/last and reversed views fit a common API. The core reason to choose it remains **sorted order**, not insertion order.
