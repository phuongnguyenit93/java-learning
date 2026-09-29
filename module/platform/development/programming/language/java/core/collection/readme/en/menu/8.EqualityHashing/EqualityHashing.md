# Equality and Hashing in Collections

Collections rely on element contracts to answer practical questions such as “is this element already present?”, “where is this key?”, and “are these two values the same logical element?”. A mistake in equality, hashing, or comparison can therefore make a collection appear to “lose” data even though the object still exists inside the structure.

This chapter focuses on the **collection consequences of equality, hashing, and comparison**. Designing `equals` and `hashCode` correctly is owned by the `object-contract` module.

## <a id="element-equality-effects">How Element Equality Affects Collections</a>

Equality matters beyond `HashSet` and `HashMap`. Many collection operations use `equals` to decide whether something matches:

- `List.contains(x)` and `List.remove(x)` locate elements through equality.
- `HashSet` uses `hashCode` to narrow the candidate area and `equals` to confirm the actual match.
- `HashMap` applies the same idea to keys.
- Equality of `List` and several other collections is built from equality of their elements.

For a simple record, Java derives equality from all record components:

```java
record User(long id, String email) {}

List<User> users = new ArrayList<>();
users.add(new User(10, "a@example.com"));

System.out.println(users.contains(new User(10, "a@example.com"))); // true
```

If the business means “users with the same id have the same identity” but `equals` also includes email, the collection will follow the Java contract that was encoded, not the business rule that was only intended. Check the element equality model before blaming the collection.

## <a id="hash-bucket-boundary">The Hash-Bucket Boundary</a>

A useful mental model for hash-based collections is:

```text
hashCode()
   ↓
narrow candidate bucket/area
   ↓
equals()
   ↓
confirm the matching key/element
```

A hash does not prove equality. Different objects may collide and enter the same candidate area; `equals` must still distinguish them. Conversely, if two objects are equal but produce different hash codes, a hash-based collection may search different areas and the lookup/uniqueness contract breaks.

```java
Map<Long, Order> byId = new HashMap<>();
byId.put(101L, new Order(101L, 1L, 20_000));

Order order = byId.get(101L);
```

Exact buckets, tree bins, and resize thresholds are JDK implementation details and should not become business assumptions. The contract-level model is enough: hashing narrows the search, equality confirms the match, and key state that participates in those calculations must remain stable while the key is stored.

## <a id="sorted-equality-boundary">Equality in Sorted Collections</a>

`TreeSet` and `TreeMap` have a different boundary: they organize data by `compareTo` or a `Comparator`. When comparison returns `0`, the sorted collection treats the two values/keys as occupying the same logical position for uniqueness.

`BigDecimal` is a classic example:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));      // false
System.out.println(a.compareTo(b));   // 0

Set<BigDecimal> hashed = new HashSet<>(List.of(a, b));
Set<BigDecimal> sorted = new TreeSet<>(List.of(a, b));

System.out.println(hashed.size()); // 2
System.out.println(sorted.size()); // 1
```

This is not a random `TreeSet` bug. The set uses equivalence under its ordering relation to determine positions. However, when ordering is inconsistent with `equals`, the sorted set can violate the usual equality intuition of `Set` and surprise callers.

When designing a comparator for `TreeSet` or `TreeMap`, ask: “If this comparator returns `0`, do I really want the collection to treat these values as the same element/key?”. If not, the comparator needs an appropriate tie-breaker.

## <a id="collection-equality-boundary">Not Every Collection Has List/Set-Style Value Equality</a>

`Collection<E>` itself does **not define one universal value-equality contract for every collection implementation**. More specific subtypes may define their own equality rules.

- `List` defines equality as **same size + equal elements at corresponding positions**.
- `Set` defines equality as **the same members**, regardless of encounter order.
- `Map` defines equality as **the same mappings**.
- `Queue` generally does not impose a common element-based equality; queue/deque implementations such as `ArrayDeque` and `PriorityQueue` retain identity-based `equals/hashCode` from `Object`.

```java
Queue<Integer> a = new ArrayDeque<>(List.of(1, 2, 3));
Queue<Integer> b = new ArrayDeque<>(List.of(1, 2, 3));

System.out.println(a.equals(b)); // false: different objects
```

`LinkedList` is an important case where multiple interface roles matter: it is a `List`, so its equality follows the List value-equality contract even though it can also be used as a `Deque`.

Do not generalize “same elements means all Collections are equal.” Whole-container equality belongs to the relevant subtype/implementation contract, not to `Collection` as a universal rule.

## <a id="collection-equality-contracts">How List, Set, and Map Equality Differ</a>

Element equality is the raw material, but each abstraction defines **whole-container equality** according to its own semantics:

| Abstraction | Two containers are equal when... |
| --- | --- |
| `List` | they have the same size and equal elements at each corresponding position |
| `Set` | they have the same size and the same members; encounter order does not matter |
| `Map` | they contain the same `key → value` mappings; encounter order does not matter |

```java
List<Integer> listA = List.of(1, 2);
List<Integer> listB = List.of(2, 1);
System.out.println(listA.equals(listB)); // false

Set<Integer> setA = new HashSet<>(List.of(1, 2));
Set<Integer> setB = new LinkedHashSet<>(List.of(2, 1));
System.out.println(setA.equals(setB));   // true
```

This is why choosing `List` versus `Set` is not only a performance choice. The abstraction also defines **what the container's logical value means**. The complete object-level laws of `equals/hashCode` remain in the Object Contract module.

## <a id="mutable-element-risk">The Risk of Mutable Keys and Elements</a>

Hash-based and sorted collections assume that the state determining an element's/key's position does not silently change while it is stored.

Consider a mutable key:

```java
final class UserKey {
    long id;
    String region;

    UserKey(long id, String region) {
        this.id = id;
        this.region = region;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserKey that)) return false;
        return id == that.id && Objects.equals(region, that.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, region);
    }
}

UserKey key = new UserKey(10, "VN");
Map<UserKey, String> names = new HashMap<>();
names.put(key, "Phuong");

key.region = "SG"; // changes data used by hashCode/equals

System.out.println(names.get(key)); // the entry is no longer guaranteed to be found
```

The stored entry does not automatically move to the bucket associated with the new hash. Likewise, if an element in a `TreeSet` changes a field read by its comparator, the tree is not automatically re-sorted and lookup/order invariants can break.

A safer design is to use keys with stable identity, prefer immutable key objects, or remove an element before changing positioning state and insert it again afterwards. Mutating a `Map` **value** does not create this same key-position problem when the value does not participate in key identity, although application invariants still need to be maintained separately.
