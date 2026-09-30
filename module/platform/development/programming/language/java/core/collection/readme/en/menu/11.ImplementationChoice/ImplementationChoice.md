# Choosing Collection Implementations

The final roadmap milestone, **Choosing Implementations and Specialized Collections**, starts by combining the contracts learned so far into a practical decision process. Collection choice should begin with the **semantics the problem needs**: whether indexing matters, whether duplicates are valid, whether lookup is by key, what order must be preserved, and which element should be removed next. Once the contract is correct, complexity, memory, mutation rules, null policy, and workload characteristics can refine the choice.

Choosing only because “`HashMap` is fast” or “`LinkedList` insertion is O(1)” is often misleading because complexity depends on the surrounding operation. Inserting into the middle of a `LinkedList` still requires finding the node first when the caller only has an index.

## <a id="list-choice">Choosing a List: ArrayList vs LinkedList</a>

`ArrayList` is a strong default for most sequences that need indexing and traversal:

- `get(index)`: O(1).
- append: amortized O(1).
- middle insert/remove: O(n) because elements must shift.
- elements live in an array-backed layout with generally good locality.

`LinkedList` uses doubly linked nodes:

- `get(index)`: O(n).
- add/remove at either end: O(1).
- add/remove at a position **after the caller already has the node/iterator there**: relinking is O(1).
- each element carries additional link references and has poorer locality than an array-backed structure.

So “many inserts” is not enough reason to choose `LinkedList`. If each insert starts with `get(i)` or traversal from an endpoint, locating the position still costs O(n). For append, iteration, and random access workloads, `ArrayList` is usually the better fit.

If the real requirement is fast operations at both ends, look at the `Deque` abstraction; `ArrayDeque` often expresses that intent more directly.

## <a id="set-choice">Choosing a Set: HashSet, LinkedHashSet, or TreeSet</a>

These implementations all enforce uniqueness but differ in ordering and cost:

| Implementation | Order | Lookup/add/remove | Good fit |
| --- | --- | --- | --- |
| `HashSet` | No encounter-order guarantee | expected O(1) | Membership/uniqueness only |
| `LinkedHashSet` | Insertion-based encounter order | expected O(1) | Uniqueness plus stable traversal order |
| `TreeSet` | Sorted by natural order/comparator | O(log n) | Always-sorted data and range/navigation operations |

In Java 21, `LinkedHashSet` participates in `SequencedSet`, so its order is also directly exposed through first/last operations and `reversed()`.

`TreeSet` requires a valid ordering and uses comparison results to determine uniqueness. If a comparator returns `0` for objects the business still needs to keep separately, either the comparator or the chosen structure does not match the requirement.

## <a id="map-choice">Choosing a Map: HashMap, LinkedHashMap, or TreeMap</a>

Map choice follows key lookup semantics and ordering:

| Implementation | Key order | Lookup/put/remove | Distinguishing trait |
| --- | --- | --- | --- |
| `HashMap` | No encounter-order guarantee | expected O(1) | General-purpose key lookup |
| `LinkedHashMap` | Insertion order or access order | expected O(1) | Ordered traversal; can support access-order caches |
| `TreeMap` | Sorted by key | O(log n) | Range queries and first/last/nearest-key navigation |

In Java 21, `LinkedHashMap` is a `SequencedMap`, making first/last/reversed encounter order explicit. Its `accessOrder=true` mode can reorder entries on access, which is useful for LRU-style structures when paired with an appropriate eviction policy.

If keys are enum constants, do not automatically choose `HashMap`. The next chapter's `EnumMap` expresses that domain more directly and uses a specialized representation.

## <a id="queue-choice">Choosing a Queue: ArrayDeque, LinkedList, or PriorityQueue</a>

For FIFO/LIFO behavior or operations at both ends, `ArrayDeque` is usually the general-purpose choice:

- efficient insertion/removal at both ends;
- queue behavior through `offerLast/pollFirst`;
- stack behavior through `push/pop`;
- `null` elements are not permitted.

`LinkedList` also implements both `Deque` and `List`. It is appropriate when the linked-list contract/behavior itself is useful; using it only because a queue is needed generally offers no clear advantage over `ArrayDeque` and adds node overhead.

`PriorityQueue` solves a different problem: it exposes the element with the **highest priority according to its ordering** at the head (the least element under the comparator, unless the comparator is reversed).

```java
PriorityQueue<Order> queue = new PriorityQueue<>(
        Comparator.comparingInt(Order::totalCents).reversed()
);

queue.offer(new Order(1, 1, 20_000));
queue.offer(new Order(2, 2, 10_000));

System.out.println(queue.peek().id()); // 1
```

A `PriorityQueue` iterator is **not guaranteed** to return all elements in priority order. Repeated `poll()` operations are required to consume them by priority. Elements with equal priority also have no stable ordering guarantee by default.

## <a id="null-policy-matrix">Null Policies of Common Implementations</a>

Null handling belongs to the **implementation contract**; it cannot be inferred from `List`, `Set`, `Map`, or `Queue` alone. A compact reference:

| Implementation | Basic `null` policy |
| --- | --- |
| `ArrayList` | permits `null` elements |
| `LinkedList` | permits `null` elements, although nulls are ambiguous in queue-style APIs |
| `HashSet` / `LinkedHashSet` | permit one `null` element |
| `TreeSet` | ordinary natural ordering rejects `null`; a custom comparator can define handling |
| `HashMap` / `LinkedHashMap` | permit one `null` key and multiple `null` values |
| `TreeMap` | ordinary natural ordering rejects `null` keys; a custom comparator can define handling; null values are permitted |
| `ArrayDeque` | rejects `null` |
| `PriorityQueue` | rejects `null` |
| `List.of` / `Set.of` / `Map.of` and the corresponding `copyOf` families | reject `null` elements/keys/values |

Do not treat this table as a reason to design APIs around `null`. Queue operations such as `poll/peek` use `null` to represent “no element,” so storing null values would make the semantics ambiguous even in a different implementation that happened to permit them.

## <a id="choice-by-characteristics">Choose by Characteristics Before Micro-Optimization</a>

A practical selection process is:

1. Choose the abstraction: `List`, `Set`, `Map`, or `Queue/Deque`.
2. Define uniqueness/key semantics and required ordering.
3. Identify hot operations: random access, membership lookup, range query, endpoint operations, priority removal, and so on.
4. Define the mutation model: fully modifiable, fixed-size/backed, unmodifiable view, or independent snapshot.
5. Check null policy and factory restrictions. For example, `List.of`/`Set.of`/`Map.of` and the `copyOf` families reject nulls; `Set.of` rejects duplicate arguments and `Map.of` rejects duplicate keys.
6. Account for memory overhead and expected data size. Linked nodes, linked encounter-order metadata, tree nodes, and spare array capacity all have different costs; Big-O alone does not describe that footprint.
7. Look for domain-specific specialization before settling on a general-purpose structure. If elements or keys come from one enum type, `EnumSet` or `EnumMap` may express the domain more precisely.
8. Benchmark only when performance is actually a concern; do not turn Big-O into guesses about micro-performance.

Using the same `Order` domain:

```text
preserve input order and allow duplicates
→ ArrayList<Order>

unique order ids, order does not matter
→ HashSet<Long>

lookup Order by id
→ HashMap<Long, Order>

lookup by id and traverse in insertion order
→ LinkedHashMap<Long, Order>

process Orders by priority
→ PriorityQueue<Order>
```

These checks can change the choice even when two implementations satisfy the same interface. A collection with the right asymptotic cost may still be wrong if it permits mutation that an API should prevent, rejects a required null, loses an ordering guarantee, violates a factory restriction, or carries unnecessary structural overhead for the workload.

Prefer returning the interface that represents the API contract, such as `List<Order>` instead of forcing callers to depend on `ArrayList<Order>`, unless implementation-specific behavior is intentionally part of that contract.

The next chapter completes this milestone with `EnumSet` and `EnumMap`. Their specialized representation is useful when the domain is an enum universe, but the primary reason to choose them is that their type and ordering contracts match that domain.

Thread safety is another selection dimension, but it should not be solved by casually swapping collection implementations in this chapter. Shared mutable data requires ownership, synchronization, and concurrent-collection reasoning from the corresponding concurrency material.
