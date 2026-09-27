# Mutable and Immutable Collections

When a method returns a collection, the important question is not only “which elements are present?” but also “who owns structural changes?”. Java offers several distinct shapes: modifiable collections, views that reject mutation, snapshots detached from later source changes, and factories that create unmodifiable collections.

These concepts govern **collection mutation**. They do not automatically make the objects stored inside the collection immutable.

## <a id="modifiable-vs-unmodifiable">Modifiable vs Unmodifiable</a>

A **modifiable** collection supports mutation operations allowed by its contract, such as `add`, `remove`, or `clear`. An **unmodifiable view** rejects mutation through the view, usually with `UnsupportedOperationException`, while the source behind that view may still change.

```java
List<Order> source = new ArrayList<>();
source.add(new Order(1, 1, 20_000));

List<Order> view = Collections.unmodifiableList(source);

// view.add(...) -> UnsupportedOperationException
source.add(new Order(2, 2, 10_000));

System.out.println(view.size()); // 2: the view sees source changes
```

So “unmodifiable” does not mean “can never change”. It means this reference or view does not provide a structural mutation path.

Java 21 extends the same idea to sequenced collections with `Collections.unmodifiableSequencedCollection`, `unmodifiableSequencedSet`, and `unmodifiableSequencedMap`. These wrappers preserve encounter-order/first-last semantics while rejecting mutation through the wrapper.

## <a id="immutable-factory">Unmodifiable Collection Factories</a>

`List.of`, `Set.of`, and `Map.of`/`Map.ofEntries` create **unmodifiable** collections whose contents are established at creation time. There is no separate mutable source collection that can later change the backing structure as there is with `Collections.unmodifiableList(source)`.

```java
List<String> users = List.of("U01", "U02", "U03");
Set<String> roles = Set.of("USER", "ADMIN");
Map<String, Integer> limits = Map.of(
        "STANDARD", 10,
        "PREMIUM", 100
);
```

Important contracts:

- These factories reject `null` elements/keys/values.
- `List.of` allows duplicate elements.
- `Set.of` rejects duplicate arguments.
- `Map.of`/`Map.ofEntries` reject duplicate keys.
- Application logic should not depend on a particular encounter order for `Set.of` or `Map.of`.

People often call these “immutable factories”, but the Java API specifies the results as **unmodifiable**. Mutable elements can still change:

```java
List<StringBuilder> values = List.of(new StringBuilder("A"));
values.getFirst().append("B");

System.out.println(values); // [AB]
```

The collection cannot add, remove, or replace an element, while the `StringBuilder` object itself remains mutable.

## <a id="copy-of">copyOf and Structural Snapshots</a>

`List.copyOf`, `Set.copyOf`, and `Map.copyOf` accept existing data and return an unmodifiable result representing the **contents at copy time**.

```java
List<Order> source = new ArrayList<>();
source.add(new Order(1, 1, 20_000));

List<Order> snapshot = List.copyOf(source);

source.add(new Order(2, 2, 10_000));

System.out.println(source.size());   // 2
System.out.println(snapshot.size()); // 1
```

The `copyOf` methods may reuse an existing instance when the input is already an appropriate unmodifiable collection. Callers should not depend on object identity; depend on contents and mutation semantics instead.

This is still a **shallow copy**: element references are copied, but the element objects are not cloned. If a mutable element changes through another reference, the snapshot observes the new state of that same object.

`copyOf` also has important input contracts:

- `List.copyOf` / `Set.copyOf` reject `null` elements;
- `Map.copyOf` rejects `null` keys and `null` values;
- `List.copyOf` preserves the source collection's iteration order in the resulting list;
- `Set.copyOf` and `Map.copyOf` **do not promise to preserve the encounter order** of a source `LinkedHashSet` or `LinkedHashMap`.

So `copyOf` is a snapshot of **contents**, but callers should not assume that every observable property of the source implementation, such as insertion order, is also snapshotted.

A useful nuance is that `Set.copyOf(collection)` can accept a source containing equal elements and retain one representative; this differs from `Set.of(a, b, ...)`, where duplicate arguments are rejected.

## <a id="backed-view-fixed-size-snapshot">Backed Views, Fixed-Size Adapters, and Snapshots</a>

These ideas are often collapsed into “a list that cannot be changed,” but their behavior is different:

| Shape | Example | Sees later source changes? | Can this reference change size? |
| --- | --- | --- | --- |
| backed mutable view | `list.subList(...)`, `map.keySet()` | yes | depends on the view/operation |
| unmodifiable backed view | `Collections.unmodifiableList(source)` | yes | no |
| fixed-size backed adapter | `Arrays.asList(array)` | yes, in both directions with the array | no, but `set` works |
| unmodifiable snapshot | `List.copyOf(source)` | not for later structural source changes | no |

A `Map` view example:

```java
Map<Long, Order> byId = new HashMap<>();
byId.put(1L, orderA);
byId.put(2L, orderB);

Set<Long> keys = byId.keySet();
keys.remove(1L);

System.out.println(byId.containsKey(1L)); // false
```

A `subList` example:

```java
List<String> source = new ArrayList<>(List.of("A", "B", "C"));
List<String> tail = source.subList(1, 3);
tail.set(0, "X");

System.out.println(source); // [A, X, C]
```

A fixed-size adapter example:

```java
String[] array = {"A", "B"};
List<String> fixed = Arrays.asList(array);

fixed.set(0, "X");
System.out.println(array[0]); // X

// fixed.add("C"); // UnsupportedOperationException
```

The important question is not only “can I call `add`?”. Ask **whether this reference shares backing storage/state with a source, which mutations are supported, and which changes are visible from the other side**.

## <a id="view-vs-copy">View vs Defensive Copy</a>

The choice should follow ownership:

```java
List<Order> source = new ArrayList<>(List.of(
        new Order(1, 1, 20_000),
        new Order(2, 2, 10_000)
));

List<Order> readOnlyView = Collections.unmodifiableList(source);
List<Order> snapshot = List.copyOf(source);
List<Order> reverseView = source.reversed(); // Java 21: reverse-ordered view

source.add(new Order(3, 3, 15_000));

System.out.println(readOnlyView.size()); // 3
System.out.println(snapshot.size());     // 2
System.out.println(reverseView.getFirst().id()); // 3
```

Choose a **view** when multiple parties should observe the same live collection and owner changes are intended to appear immediately. Choose a **defensive copy** when an API boundary needs an independent structural snapshot that is not affected by later source mutations.

Neither a view nor a copy automatically provides thread safety or deep immutability. When a collection is shared across threads, synchronization/concurrent-collection choices belong to the corresponding concurrency material.
