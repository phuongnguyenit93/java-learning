# Core Collection Contracts — Map

The **Core Collection Contracts** milestone next reaches a requirement that does not fit `Collection<E>` well: finding a value through a **separate key**. An application may own many `Order` objects while incoming requests identify them by `orderId`. `Map<K, V>` models that relationship directly.

## <a id="map-semantics">Map Semantics</a>

`Map<K, V>` stores `key → value` mappings. Each key appears at most once, while multiple keys may map to equal values.

```java
Map<Long, Order> orderById = new HashMap<>();

orderById.put(1001L, new Order(1001, 7, 120));
orderById.put(1002L, new Order(1002, 8, 90));

Order order = orderById.get(1002L);
```

Putting an existing key replaces its previous mapping and returns the old value:

```java
Order previous = orderById.put(
        1002L,
        new Order(1002, 8, 110)
);
```

That differs from `Set`: a set asks “is this element already present?”, while a map asks “which value is associated with this key?”

## <a id="map-basic-contract">Basic Map Operations</a>

Because `Map` does not extend `Collection`, it has its own core vocabulary. A beginner should understand these operations before moving to `compute` and `merge`:

| Intent | Operation |
| --- | --- |
| number of mappings | `size()` |
| check whether the map is empty | `isEmpty()` |
| read a value by key | `get(key)` |
| add/replace a mapping | `put(key, value)` |
| test key presence | `containsKey(key)` |
| test value presence | `containsValue(value)` |
| remove a mapping by key | `remove(key)` |
| remove every mapping | `clear()` |
| copy all mappings from another map | `putAll(otherMap)` |

```java
Map<Long, Order> orders = new HashMap<>();
orders.put(1001L, orderA);
orders.put(1002L, orderB);

boolean has1001 = orders.containsKey(1001L);
boolean containsOrderB = orders.containsValue(orderB);
Order found = orders.get(1002L);

orders.remove(1001L);
```

`containsKey` is usually more central than `containsValue`: lookup by key is the primary Map abstraction, while searching by value commonly requires examining many mappings and is not the main reason to choose a map.

## <a id="map-entry-model">Map.Entry Represents One key → value Mapping</a>

`Map.Entry<K, V>` represents **one mapping** inside a map: a key paired with its current value.

```java
for (Map.Entry<Long, Order> entry : orderById.entrySet()) {
    Long id = entry.getKey();
    Order order = entry.getValue();
    System.out.println(id + " -> " + order);
}
```

When an algorithm needs both key and value, `entrySet()` exposes exactly the unit being processed and avoids the pattern “iterate keys, then call `get(key)` again.” Some entry views support `setValue`, but that is an optional/mutable-view behavior of the particular map; code should not assume every `Map.Entry` can be modified.

Three collection views expose the map from different angles:

```java
Set<Long> keys = orderById.keySet();
Collection<Order> values = orderById.values();
Set<Map.Entry<Long, Order>> entries = orderById.entrySet();
```

When both key and value are needed, iterating `entrySet()` is usually clearer than iterating keys and then calling `get(key)`:

```java
for (Map.Entry<Long, Order> entry : orderById.entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue());
}
```

Null and ordering policies depend on the implementation. `HashMap` permits one null key and multiple null values; `Map.of(...)` rejects both. If null values are permitted, `get(key) == null` cannot distinguish “no mapping” from “mapped to null”; use `containsKey` when that distinction matters.

Plain `Map` has no universal encounter-order promise. Since Java 21, `SequencedMap` **standardizes** first/last/reversed vocabulary for maps that participate in that abstraction. Having a defined order does not imply that a map must implement `SequencedMap`: `EnumMap`, for example, iterates in enum declaration order but does not implement `SequencedMap`.

## <a id="map-views">keySet, values, and entrySet Are Backed Views</a>

`keySet()`, `values()`, and `entrySet()` do not normally create three independent collections. They are **views backed by the original map**: supported changes through one side can be observed from the other.

```java
Map<Long, String> names = new HashMap<>();
names.put(1L, "An");
names.put(2L, "Binh");

Set<Long> keys = names.keySet();
keys.remove(1L);

System.out.println(names.containsKey(1L)); // false

names.put(3L, "Chi");
System.out.println(keys.contains(3L));      // true
```

The views do not support every mutation operation. For example, `keySet().add(key)` does not have enough information to create a mapping because there is no value, so that operation is unsupported. Create an explicit copy when an independent snapshot is required:

```java
Set<Long> keySnapshot = Set.copyOf(names.keySet());
List<String> valueSnapshot = List.copyOf(names.values());
```

This is the same conceptual family as `List.subList`: a **view shares backing state**, while a copy has its own structural state.

## <a id="map-default-operations">Everyday Map Operations Before compute/merge</a>

Before `compute` or `merge` is needed, many map updates are clearer through simpler operations:

```java
Order fallback = new Order(-1, -1, 0);
Order found = orderById.getOrDefault(1001L, fallback);

orderById.putIfAbsent(1003L, new Order(1003, 9, 75));
orderById.replace(1003L, new Order(1003, 9, 80));

boolean replaced = orderById.replace(
        1003L,
        new Order(1003, 9, 80),
        new Order(1003, 9, 85)
);

boolean removed = orderById.remove(1003L, new Order(1003, 9, 85));
```

- `getOrDefault` supplies a fallback only when the key has **no mapping**; if the key exists and maps to `null`, the result is still `null`; the method does not insert the fallback;
- `putIfAbsent` writes only when the key has no non-null mapping;
- `replace` changes a value only when the key exists according to the selected overload;
- `remove(key, value)` removes only when both the key and current value match.

These default methods often express intent better than a `containsKey → get → put/remove` sequence. Do not infer cross-thread atomicity from the plain `Map` interface; concurrent implementations have their own contracts in the concurrency curriculum.

## <a id="map-equality">Map Equality Is Based on Mappings</a>

Two maps are equal when they represent the same set of `key → value` mappings. Encounter order and concrete implementation do not determine `Map.equals`.

```java
Map<Long, String> a = new HashMap<>();
a.put(1L, "A");
a.put(2L, "B");

Map<Long, String> b = new LinkedHashMap<>();
b.put(2L, "B");
b.put(1L, "A");

System.out.println(a.equals(b)); // true
```

Key and value comparisons still depend on the equality contracts of those objects. Designing `equals/hashCode` belongs to the Object Contract module; the collection-specific point is that changing map ordering does not by itself make two maps unequal when their mappings are unchanged.

## <a id="hashmap-model">HashMap Model</a>

`HashMap` is a general-purpose implementation when fast key lookup matters and no specific encounter order is required.

A useful mental model is:

```text
key
 ↓ hashCode()
select candidate bucket/region
 ↓
equals() confirms equivalent key
 ↓
associated value
```

```java
Map<Long, Order> orderById = new HashMap<>();
for (Order order : orders) {
    orderById.put(order.id(), order);
}

Order found = orderById.get(1002L);
```

With reasonable hash distribution, `put`, `get`, and `remove` have expected near-O(1) cost. That is an expected hash-table characteristic, not an absolute worst-case guarantee for every key and state.

Like `HashSet`, correctness depends on keys keeping stable `equals/hashCode` behavior while stored in the map. If a field participating in hashing changes after `put`, later lookup may fail from the caller's perspective.

```java
record OrderKey(long id) {}

Map<OrderKey, Order> map = new HashMap<>();
map.put(new OrderKey(1001), order);
```

A record is convenient because its identity is value-based and immutable. Keys do not have to be records; the requirement is a suitable, stable equality/hash contract.

`HashMap` does not guarantee iteration order. Output that happens to match insertion order is still not a contract.

## <a id="linkedhashmap-order">LinkedHashMap Order</a>

`LinkedHashMap` keeps hash-map mappings while also maintaining a defined encounter order. Two modes matter.

### Insertion order

The usual constructor keeps keys in the order of their first insertion:

```java
Map<Long, Order> orders = new LinkedHashMap<>();
orders.put(30L, order30);
orders.put(10L, order10);
orders.put(20L, order20);

System.out.println(orders.keySet()); // [30, 10, 20]
```

Replacing the value for an existing key does not normally make it a newly inserted key at the end of insertion order.

### Access order

A constructor with `accessOrder = true` orders entries by access rather than first insertion:

```java
Map<Long, Order> recent = new LinkedHashMap<>(16, 0.75f, true);
recent.put(1L, order1);
recent.put(2L, order2);
recent.put(3L, order3);

recent.get(1L);
System.out.println(recent.keySet()); // [2, 3, 1]
```

That mode is a useful building block for some cache/LRU patterns, although production caches also need explicit eviction, concurrency, and memory policies. `LinkedHashMap` supplies the ordering mechanism, not an entire cache design.

In Java 21, `LinkedHashMap` implements `SequencedMap`. Callers can work with first/last entries, explicit positioning, and reversed views without first materializing a list of keys. Encounter order becomes an explicit type capability.

## <a id="treemap-order">TreeMap Order</a>

`TreeMap` keeps mappings in **sorted key order**.

```java
Map<Long, Order> orders = new TreeMap<>();
orders.put(30L, order30);
orders.put(10L, order10);
orders.put(20L, order20);

System.out.println(orders.keySet()); // [10, 20, 30]
```

Keys are ordered by their natural ordering or by a `Comparator` supplied to the constructor.

```java
record UserKey(long id, String region) {}

Map<UserKey, User> users = new TreeMap<>(
        Comparator.comparing(UserKey::region)
                  .thenComparingLong(UserKey::id)
);
```

As with `TreeSet`, a comparison result of zero means two keys occupy the same logical map position. If the comparator ignores an important field, putting a second key can replace the value associated with the first.

For a sorted map, it is therefore strongly preferable that the ordering be **consistent with `equals`**. A `TreeMap` whose ordering treats two unequal keys as comparison-equal still has defined behavior, but it no longer cleanly follows the general `Map` equality contract because key equivalence inside the tree is being decided by comparison rather than by `equals`.

`TreeMap` typically provides O(log n) `get`, `put`, and `remove` operations in exchange for navigable operations such as `firstEntry`, `lastEntry`, `lowerEntry`, `floorEntry`, `ceilingEntry`, and `higherEntry`.

With ordinary natural ordering, `TreeMap` does not accept a null key because it cannot be compared under that contract. Null values are allowed. A custom comparator can define null-key handling, but doing so should be an explicit API decision.

In Java 21, the sorted-map hierarchy also participates in `SequencedMap`, so first/last/reversed semantics have a common interface. The reason to choose `TreeMap` remains **sorted lookup and navigation by key**.

## <a id="map-compute-merge">Compute and Merge Updates</a>

Many map operations have the shape “read the old value, calculate a new value, write it back.” `Map` has methods that express this pattern directly.

Count orders per user:

```java
Map<Long, Integer> orderCountByUser = new HashMap<>();

for (Order order : orders) {
    orderCountByUser.merge(order.userId(), 1, Integer::sum);
}
```

`merge(key, value, remappingFunction)` requires the supplied `value` to be non-null:

- uses the supplied value when there is no mapping or the current mapping is null;
- invokes the remapping function with old/new values when a non-null value exists;
- removes the mapping if the remapping function returns null.

Create a collection per key:

```java
Map<Long, List<Order>> ordersByUser = new HashMap<>();

for (Order order : orders) {
    ordersByUser
            .computeIfAbsent(order.userId(), id -> new ArrayList<>())
            .add(order);
}
```

`computeIfAbsent` creates a value only when no non-null mapping exists; if its mapping function returns null, no mapping is recorded.

When recalculation needs both the key and current value:

```java
totals.compute(userId, (id, current) ->
        current == null ? order.totalCents() : current + order.totalCents()
);
```

If the `compute` remapping function returns null, the mapping is removed.

These APIs keep update logic close to its intent and often read better than `get → if → put`. They do not imply that every `Map` makes a compound update atomic across threads; atomicity depends on contracts such as `ConcurrentMap`/`ConcurrentHashMap` and belongs to the concurrency curriculum.
