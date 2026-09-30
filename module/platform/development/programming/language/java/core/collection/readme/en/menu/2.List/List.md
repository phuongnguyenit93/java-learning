# Core Collection Contracts — List

Once the hierarchy is clear, the **Core Collection Contracts** milestone begins with `List`: data where **order and position matter**. A list of orders in arrival order is a natural example: equal orders may both be present, and “the first order” is different from “the third order.”

## <a id="list-semantics">List Semantics</a>

`List<E>` is a collection with a defined encounter order and positional access through zero-based indexes.

```java
List<Order> orders = new ArrayList<>();
orders.add(new Order(1001, 7, 120));
orders.add(new Order(1002, 8, 90));
orders.add(new Order(1003, 7, 120));

Order first = orders.get(0);
Order second = orders.get(1);
```

Order is part of the `List` contract. Appending places an element at the end; inserting at index 1 shifts later positions.

Lists also permit equal elements:

```java
List<String> statuses = new ArrayList<>();
statuses.add("NEW");
statuses.add("NEW");

System.out.println(statuses.size()); // 2
```

That differs from `Set`, whose primary contract is uniqueness. If every occurrence is meaningful to the domain, `List` is often the appropriate starting point.

Typical operations are:

```java
orders.add(order);          // append
orders.add(1, order);       // insert by position
orders.get(1);              // read by position
orders.set(1, replacement); // replace at a position
orders.remove(1);           // remove by position
```

Since Java 21, `List` is a `SequencedCollection`. Lists therefore share first/last operations such as `getFirst()`, `getLast()`, `addFirst()`, `addLast()`, and `reversed()`. Whether mutation is supported still depends on the concrete list; an unmodifiable list may throw `UnsupportedOperationException` for modifying operations.

## <a id="list-practical-boundaries">Practical List Boundaries</a>

### `remove(index)` vs `remove(value)`

`List` overloads `remove`, which is easy to misread for numeric element types:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));

numbers.remove(1);                    // removes index 1 -> 20
numbers.remove(Integer.valueOf(10));  // removes value 10
```

A primitive `int` argument selects `remove(int index)`. To remove an `Integer` value, pass an `Integer` object explicitly.

### `subList` is a view, not a copy

```java
List<String> source = new ArrayList<>(List.of("A", "B", "C", "D"));
List<String> middle = source.subList(1, 3); // [B, C]

middle.set(0, "X");
System.out.println(source); // [A, X, C, D]
```

`subList(from, to)` returns a **backed view** of a range in the original list. Supported mutation through the view is reflected in the source and vice versa. If the backing list is structurally modified in any way other than through the returned sub-list, the semantics of that sub-list become undefined. A particular implementation may detect the mismatch and throw `ConcurrentModificationException`, but CME is not the `subList` contract itself.

Create an explicit copy when independence is required:

```java
List<String> snapshot = new ArrayList<>(source.subList(1, 3));
```

### `Arrays.asList` is neither a normal `ArrayList` nor `List.of`

```java
Order[] array = {orderA, orderB};

List<Order> fixedSize = Arrays.asList(array);
fixedSize.set(0, orderC);  // valid; array[0] changes too

// fixedSize.add(orderD);    // UnsupportedOperationException
// fixedSize.remove(orderB); // UnsupportedOperationException

List<Order> mutableCopy = new ArrayList<>(fixedSize);
List<Order> unmodifiableCopy = List.copyOf(fixedSize);
```

`Arrays.asList(array)` creates a **fixed-size list backed by the array**: `set` is supported, while size-changing `add/remove` operations are not. `List.of(...)`/`List.copyOf(...)` produce unmodifiable lists, while `new ArrayList<>(...)` creates a structurally independent mutable list.

## <a id="arraylist-model">ArrayList Model</a>

The useful mental model for `ArrayList` is a **resizable array**. It has contiguous indexed storage plus a logical `size` indicating how many elements are in use.

Appending is cheap while spare capacity exists:

```text
capacity = 8
size     = 5

[A][B][C][D][E][ ][ ][ ]
                  ↑
              append here
```

When capacity is exhausted, the implementation allocates a larger array and copies elements into it. Because resizing happens only occasionally, appending with `add(E)` is **amortized O(1)** even though an individual resize is O(n).

Indexed access fits the array model well:

```java
Order order = orders.get(500);
```

`get(index)` is O(1) because the location can be calculated directly. Inserting or removing in the middle usually requires shifting later elements:

```text
before: [A][B][C][D]
insert X at index 1
after : [A][X][B][C][D]
              └────────→ shifted elements
```

Typical costs are therefore:

| Operation | `ArrayList` |
| --- | --- |
| `get(index)` | O(1) |
| `set(index, value)` | O(1) |
| append | amortized O(1) |
| middle insert/remove | O(n) because elements shift |
| search by value | O(n) for a linear scan |

Big-O describes growth in cost, not an absolute benchmark. Cache locality and object overhead also affect real performance. For workloads dominated by indexed reads and appends, `ArrayList` is usually a strong default `List` implementation.

`ArrayList` does not provide automatic synchronization for multi-threaded access. Concurrency ownership belongs to a separate curriculum area; choosing a collection does not by itself solve synchronization.

## <a id="linkedlist-model">LinkedList Model</a>

`LinkedList` uses doubly linked nodes. Each node stores an element plus links to its predecessor and successor.

```text
null ← [A] ⇄ [B] ⇄ [C] → null
```

That representation makes operations at either end natural:

```java
Deque<Order> pending = new LinkedList<>();
pending.addFirst(urgentOrder);
pending.addLast(normalOrder);
```

If the implementation already has the relevant internal node, linking or unlinking it is constant-time. But the `List` API does not expose nodes to callers. To perform `get(5000)` or `add(5000, value)`, `LinkedList` must walk nodes from an end until it reaches that position, so locating the position is O(n).

```java
Order order = linkedOrders.get(5000); // traverses to the position
```

Memory usage also differs: each element needs a node with links, while `ArrayList` primarily keeps references inside an array.

For that reason, “`LinkedList` is faster for insertions and removals” is too broad. It can be effective at the ends or when an iterator is already positioned appropriately; it does not automatically win for index-based insertion because the node still has to be found.

For a pure double-ended queue or stack use case, `ArrayDeque` is commonly a clearer modern choice. `LinkedList` remains useful when the program truly needs the `List` contract together with end operations, but it should be chosen from the actual workload.

## <a id="list-equality">List Equality Is Order-Sensitive</a>

The `List.equals` contract checks more than “contains the same elements.” Two lists are equal when:

1. the other object is also a `List`;
2. both lists have the same size;
3. each pair of elements at the **same position** is equal.

```java
List<Long> a = List.of(10L, 20L, 30L);
List<Long> b = List.of(10L, 20L, 30L);
List<Long> c = List.of(30L, 20L, 10L);

System.out.println(a.equals(b)); // true
System.out.println(a.equals(c)); // false
```

Encounter order is therefore part of a list's logical value. If the domain says order is irrelevant and uniqueness is what matters, `Set` may be the better abstraction.

The full `equals/hashCode` laws belong to the Object Contract module. The collection-specific point is that element equality affects operations such as `contains`, `indexOf`, and whole-list equality, while ordering gives `List` different semantics from `Set`.
