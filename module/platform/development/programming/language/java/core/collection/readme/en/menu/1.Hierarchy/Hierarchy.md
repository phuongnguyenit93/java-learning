# Java Collections Hierarchy

Before learning `List`, `Set`, `Map`, or `Queue`, start with a simpler question: **what is a collection, and why does a Java program need one?**

Suppose a program has only three orders. It can store them in three separate variables:

```java
Order first = new Order(1001, 7, 120);
Order second = new Order(1002, 8, 90);
Order third = new Order(1003, 7, 150);
```

Real data rarely stops at three items. The number of orders may be 0, 10, 1,000, or may change while the program is running. Code therefore needs a way to **group many objects and operate on the group as one object**: add values, remove values, search, iterate, count, preserve order, enforce uniqueness, or look values up by key.

That is the broad problem solved by the Java Collections Framework.

We will reuse a small dataset throughout this module:

```java
record User(long id, String name) {}
record Order(long id, long userId, int totalCents) {}
```

The same orders can be kept in creation order, reduced to unique user IDs, indexed by order ID, or placed in a processing queue. All of those structures contain multiple values, but they promise different behavior.

## <a id="collection-purpose">What Is a Collection and Why Use One?</a>

At the beginner mental-model level, a **collection** is an object that manages **a group of elements** so application code does not have to manage every value as a separate variable.

For example:

```java
List<Order> orders = new ArrayList<>();

orders.add(new Order(1001, 7, 120));
orders.add(new Order(1002, 8, 90));
orders.add(new Order(1003, 7, 150));

System.out.println(orders.size());

for (Order order : orders) {
    System.out.println(order.id());
}
```

Instead of creating `order1`, `order2`, `order3`, and so on, the program works with **one object representing the whole group**.

Read the declaration from left to right:

```java
List<Order> orders = new ArrayList<>();
```

- `List<Order>` says the variable follows the `List` contract and its element type is `Order`;
- `new ArrayList<>()` chooses `ArrayList` as the concrete implementation;
- the diamond `<>` lets the compiler infer `Order` from the target type, so `new ArrayList<Order>()` does not have to repeat it.

Generic collection element types are reference types. Primitive values use wrapper types such as `List<Integer>` for `int` and `List<Long>` for `long`; boxing/unboxing details belong to the language and Generics topics.

### The problem before collections

Arrays already let Java store several values of one component type:

```java
Order[] orders = new Order[100];
```

Arrays remain useful when the size is fixed or an API/performance requirement calls for them. But using an array for dynamic application data often means manually solving recurring problems:

```text
The array is full. What now?
→ allocate a larger array and copy values

Remove an element from the middle?
→ shift remaining elements manually

Require uniqueness?
→ write duplicate-detection logic

Look up an order by id?
→ scan or build a separate index structure

Need FIFO/LIFO processing?
→ manage head/tail positions yourself
```

The Collections Framework provides standardized structures for these common responsibilities instead of making every application reinvent them.

### What is the Java Collections Framework?

The **Java Collections Framework (JCF)** is the standard set of interfaces, implementations, and supporting algorithms/utilities for representing and manipulating groups of data.

A useful mental model is:

```text
Many data elements
        ↓
Need one object to manage the group
        ↓
The Collections Framework provides different contracts
        ↓
List / Set / Queue / Deque / Map
        ↓
choose the contract from the behavior the problem needs
```

The same `Order` domain data may therefore be represented differently for different questions:

```java
List<Order> ordersInArrivalOrder = new ArrayList<>();
Set<Long> uniqueUserIds = new HashSet<>();
Map<Long, Order> orderById = new HashMap<>();
Deque<Order> pendingOrders = new ArrayDeque<>();
```

This is why Collection is not merely “a List that stores many objects.” It is **a family of abstractions for different group-data problems**.

## <a id="collection-vs-collections">Collection, Collections, and the Collections Framework</a>

Three similarly named things should stay separate:

- `Collection<E>` is the root interface for groups of elements such as lists, sets, and queues;
- `Collections` is a utility class containing static algorithms/helpers such as `sort`, `reverse`, `binarySearch`, and wrapper factories;
- the **Java Collections Framework** is the whole family of collection/map interfaces, implementations, and related algorithms/utilities.

`Map<K, V>` belongs to the Java Collections Framework but does not extend `Collection<E>`; the next section explains why.

Once the learner understands **why a group abstraction is needed**, the hierarchy becomes useful: it answers **which behavior does this group require?**

A useful roadmap for the module is:

```text
Need to hold a group of values
        ↓
Need positional order and duplicates?     → List
        ↓
Need uniqueness?                          → Set
        ↓
Need lookup by a separate key?            → Map
        ↓
Need FIFO/LIFO/end/priority processing?   → Queue / Deque
        ↓
Need traversal independent of implementation? → Iterator
        ↓
Then study ordering, equality/hashCode, mutability,
fail-fast behavior, and implementation costs
```

By the end of the module, the goal is to start from **required behavior** and select an interface and implementation deliberately, instead of defaulting to `ArrayList` or `HashMap` by habit.

## <a id="collection-hierarchy">Collection Hierarchy</a>

The useful way to learn the hierarchy is to connect each interface to a question, rather than memorize a tree of names.

```text
Iterable<E>
    ↓
Collection<E>
    ├── List<E>        → ordered, positional, duplicates allowed
    ├── Set<E>         → uniqueness
    │     └── SortedSet<E>       → sorted set contract
    │            └── NavigableSet<E> → neighbor navigation
    └── Queue<E>       → insertion/removal behavior for processing
          └── Deque<E> → operations at both ends

Map<K, V>              → key → value association on a separate branch
    └── SortedMap<K, V>       → sorted-key map contract
           └── NavigableMap<K, V> → neighbor navigation by key
```

`Collection<E>` extends `Iterable<E>`, giving collections a common traversal protocol. `List`, `Set`, and `Queue` refine the contract for different uses. `SortedSet` and `NavigableSet` progressively add sorted and neighbor-navigation behavior. On the separate map branch, `SortedMap` and `NavigableMap` do the equivalent work for keys. `Deque` is a queue-like contract with two ends.

After that basic hierarchy is clear, Java 21 adds **sequenced interfaces** that give structures with a defined encounter order a common first/last/reversed vocabulary:

```text
SequencedCollection<E>
├── List<E>
├── Deque<E>
└── SequencedSet<E>

SequencedMap<K, V>     → ordered first/last behavior on the Map branch
```

There are a few bridge relationships worth reading precisely: `List` extends `SequencedCollection`; `Deque` is both a `Queue` and a `SequencedCollection`; and `SequencedSet` is both a `Set` and a `SequencedCollection`. These interfaces give ordered collections a common vocabulary such as `getFirst()`, `getLast()`, and `reversed()`. They do not change the fundamental decision: choose the contract that matches the data behavior first.

In Java 21, `SortedSet` also extends `SequencedSet`, while `SortedMap` also extends `SequencedMap`. As a result, `TreeSet` and `TreeMap` expose sequenced behavior through their sorted/navigable interfaces without losing their main sorted-navigation contract.

## <a id="sorted-navigable-contracts">Sorted and Navigable Contracts</a>

At the entry-chapter level, only their roles need to be clear:

- `SortedSet` / `SortedMap` add a contract that data is **observed through a maintained ordering**;
- `NavigableSet` / `NavigableMap` add nearest-neighbor and range-navigation capabilities;
- `TreeSet` and `TreeMap` are familiar implementations of these contracts.

Concrete operations such as `lower`, `floor`, `ceiling`, `higher`, `subSet`, and `subMap` become meaningful only after the learner understands `Set`, `Map`, and ordering. Their detailed mechanics therefore belong later in Ordering/Sorting rather than in the opening roadmap.

## <a id="collection-api-contract">Shared Collection API Contract</a>

Most `Collection<E>` implementations share a small vocabulary. Learning it once prevents relearning the same operations separately for every `List`, `Set`, or `Queue`:

| Intent | Shared operation |
| --- | --- |
| number of elements | `size()` |
| check whether there are no elements | `isEmpty()` |
| membership test | `contains(value)` |
| obtain the traversal protocol | `iterator()` |
| add one element | `add(value)` |
| remove one matching element | `remove(value)` |
| remove elements matching a condition | `removeIf(predicate)` |
| remove all elements | `clear()` |
| contain every element from another collection | `containsAll(other)` |
| add elements from another collection | `addAll(other)` |
| remove elements also present in another collection | `removeAll(other)` |
| keep only elements also present in another collection | `retainAll(other)` |

```java
Collection<Order> selected = new ArrayList<>();
selected.add(orderA);
selected.add(orderB);

boolean hasOrderA = selected.contains(orderA);
boolean hasAll = selected.containsAll(List.of(orderA, orderB));

selected.remove(orderA);
selected.addAll(List.of(orderC, orderD));
```

Mutating methods in `Collection` are **optional operations**. A concrete collection may deliberately reject them by throwing `UnsupportedOperationException`. For example, a value returned by `List.of(...)` is still a `List`, but its mutating operations are unsupported. The interface tells callers which operations exist; the concrete object's contract tells callers which optional mutations are supported.

`toArray` is the standard boundary when an API requires an array:

```java
Order[] orderArray = selected.toArray(new Order[0]);
Object[] objectArray = selected.toArray();
```

`toArray()` without an argument returns `Object[]`; `toArray(new Order[0])` asks for an array whose runtime component type is `Order`. Converting to an array creates an array representation of the current elements; it does not turn the collection and array into one shared mutable container. A generator overload is introduced immediately after method-reference syntax is explained.

## <a id="functional-syntax-boundary">Lambda and Method-Reference Syntax in Collection Examples</a>

Some Collection APIs receive **behavior** supplied by the caller, so later chapters use syntax such as:

```java
orders.removeIf(order -> order.totalCents() == 0);
Comparator.comparingLong(Order::id);
selected.toArray(Order[]::new);
```

For this module, it is enough to read them as:

```text
order -> ...
→ a function that receives an order and produces a result

Order::id
→ a reference to the behavior that obtains an Order id

Order[]::new
→ a reference to creating an Order[] of a requested size
```

Lambdas, functional interfaces, and method references have their own functional-programming curriculum. Here they are only **syntax used to pass behavior into Collection APIs**, not foundational Collection concepts.

## <a id="map-separate-hierarchy">Why Map Is Separate</a>

`Map<K, V>` is not a `Collection<V>`. A collection models a group of **elements**; a map models a group of **key-to-value mappings**. Their meaningful units are different.

For example:

```java
List<Order> orders = List.of(
        new Order(1001, 7, 120),
        new Order(1002, 7, 80)
);

Map<Long, Order> orderById = Map.of(
        1001L, orders.get(0),
        1002L, orders.get(1)
);
```

With a `List<Order>`, natural questions are “what is at position 0?” or “visit every order.” With a `Map<Long, Order>`, the natural question is “which order belongs to key 1002?” If `Map` extended `Collection`, an operation such as `add(E)` could not express that a mapping requires both a key and a value.

Map still exposes **collection views** that bridge the two models:

```java
Set<Long> ids = orderById.keySet();
Collection<Order> values = orderById.values();
Set<Map.Entry<Long, Order>> entries = orderById.entrySet();
```

Those views are useful for traversing keys, values, or entries, while `Map` keeps its own key uniqueness and lookup contract.

Since Java 21, `SequencedMap` adds first/last entry operations and reversed views for maps with a defined encounter order. It remains on the `Map` branch; it does not turn `Map` into a `Collection`.

## <a id="interface-vs-implementation">Interface vs Implementation</a>

Application code should normally type a variable by the **capability the caller needs**. The concrete class chooses how that contract is implemented.

```java
List<Order> orders = new ArrayList<>();
orders.add(new Order(1001, 7, 120));
orders.add(new Order(1002, 7, 80));
```

Here:

- `List<Order>` is the contract: ordered, positional, duplicates allowed.
- `ArrayList<Order>` is the implementation: a resizable array used to satisfy that contract.

A method that only visits orders can accept a broader type:

```java
int totalRevenue(Collection<Order> orders) {
    int total = 0;
    for (Order order : orders) {
        total += order.totalCents();
    }
    return total;
}
```

The method does not need an index or knowledge of `ArrayList` versus `HashSet`, so `Collection<Order>` accurately describes its dependency.

If another method needs `get(0)`, then `List<Order>` is the right contract. “Program to an interface” does not mean “always choose the widest interface.” It means choose the **smallest interface that still expresses every required operation**.

That boundary also makes implementation changes easier:

```java
List<Order> orders = new ArrayList<>();
// A different List implementation can be chosen if the workload changes,
// as long as callers depend only on the List contract.
```

Generics determine element types such as `List<Order>` and `Map<Long, Order>`. This module focuses on collection behavior and storage contracts; detailed variance and PECS rules belong to the Generics module.

## <a id="collection-characteristics">Collection Characteristics</a>

An implementation name becomes meaningful only after the required data characteristics are clear. Useful dimensions include:

| Characteristic | Question to ask |
| --- | --- |
| Encounter order | Does iteration order matter, and is it guaranteed? |
| Positional access | Do callers need index-based access? |
| Duplicates | May two equal values coexist? |
| Key lookup | Must a value be found through a separate key? |
| Sorted order | Must the structure remain ordered by natural order or a `Comparator`? |
| Null policy | Does the chosen implementation accept `null`? |
| Mutability | May the structure be added to, removed from, or replaced after creation? |
| Operation cost | Is the workload dominated by index access, lookup, end insertion/removal, or maintaining order? |

Do not infer null or mutability policy from an interface alone. For example, `HashMap` allows one null key, while `Map.of(...)` rejects null keys and values. `ArrayList` is resizable, while `List.of(...)` is unmodifiable.

The same domain data can therefore appear in different structures:

```java
List<Long> processingOrder = new ArrayList<>();     // order; duplicates may be valid
Set<Long> uniqueUsers = new HashSet<>();            // uniqueness
Map<Long, Order> orderById = new HashMap<>();       // lookup by id
Deque<Order> pending = new ArrayDeque<>();           // operations at both ends
```

The next chapters examine each contract in turn. Whenever an implementation appears, come back to the same question: **which requirement in the problem makes this structure appropriate?**

## <a id="complexity-mental-model">Mental Model for O(1), O(n), and O(log n)</a>

Collection implementations are often compared by **operation complexity**. A beginner does not need the full theory of Big-O yet; the goal is to read the notation used throughout this module:

| Notation | Mental model | As `n` becomes large |
| --- | --- | --- |
| `O(1)` | roughly the same amount of work regardless of element count | grows very little |
| `O(log n)` | each step discards a large portion of the search space | grows slowly |
| `O(n)` | work may grow in proportion to the number of elements | grows roughly linearly |

For example, with 1,000,000 elements an `ArrayList.get(index)` can still calculate the target position directly (`O(1)`), while searching a list by value may inspect many elements (`O(n)`). A balanced-tree structure such as `TreeMap` typically follows a path whose height grows as `O(log n)`.

Two qualifiers also appear frequently:

- **amortized O(1)** means most operations are cheap but an occasional operation is more expensive, such as an `ArrayList` resize; averaged over a long sequence, the cost remains near constant;
- **expected O(1)** means the expected cost is near constant under assumptions such as a reasonable hash distribution; it is not an absolute worst-case guarantee.

Big-O describes a **growth trend**, not a promise that one operation is always faster in every real program. Memory layout, cache locality, data size, and the actual workload still matter.
