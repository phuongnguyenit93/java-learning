# Fail-Fast Iterators

An iterator keeps traversal state. If the collection structure changes outside that iterator while traversal is in progress, the iterator's position may no longer mean what it originally meant. Many standard Java collection implementations respond with **fail-fast** behavior so that a broken traversal is detected early instead of silently continuing.

Fail-fast is a bug-detection mechanism for collection traversal. It is not a thread-synchronization mechanism and must not be the basis of correctness.

## <a id="structural-modification">Structural Modification</a>

A **structural modification** changes the structure being traversed. The exact definition is implementation-specific, but in common collections it usually includes adding or removing elements/mappings.

For `ArrayList`:

```java
List<String> users = new ArrayList<>(List.of("U01", "U02", "U03", "U04"));

users.add("U04");    // structural: size changes
users.remove("U01"); // structural: size changes
users.set(0, "U10"); // not structural for ArrayList
```

For a `Map`, adding/removing a mapping is generally structural, while replacing the value for an existing key generally does not change the key structure. Application code should not depend on internal fields such as a particular implementation's `modCount`; the collection/iterator contract is the reliable boundary.

## <a id="fail-fast-best-effort">Fail-Fast Is Best-Effort</a>

A common mental model for a fail-fast iterator is:

```text
create iterator
   ↓
record expected modification state
   ↓
collection is structurally modified outside the iterator
   ↓
iterator detects a mismatch
   ↓
ConcurrentModificationException
```

The JDK describes fail-fast behavior as **best effort**. There is no absolute guarantee that an exception will be thrown for every race or at every possible point. Code that assumes “CME will protect me if anything goes wrong” is therefore incorrect.

Fail-fast is valuable because it turns an otherwise subtle traversal bug into a clearer development/debugging signal. Correctness still comes from using a valid mutation path or choosing the appropriate collection/concurrency model.

## <a id="concurrent-modification-exception">What ConcurrentModificationException Actually Means</a>

The name `ConcurrentModificationException` makes it easy to assume that two threads are required. A single thread can trigger CME by mutating a collection through the collection reference while an iterator over that collection is active. The example below uses an `ArrayList` iterator directly so the detection point is explicit:

```java
List<String> users = new ArrayList<>(List.of("U01", "U02", "U03"));
Iterator<String> iterator = users.iterator();

System.out.println(iterator.next()); // U01

users.add("U04");                   // structural modification outside the iterator

iterator.next();                    // ArrayList iterator detects mismatch -> CME
```

Enhanced `for` over a collection also uses an iterator underneath, so the same principle applies when the collection is modified directly inside the loop. Fail-fast remains best-effort, however; code must not intentionally violate the traversal contract and rely on CME as a correctness mechanism.

Read CME as: **the iterator detected a modification that invalidated its traversal contract**. It does not prove a data race, and it is not a signal that should simply be caught and ignored.

In multithreaded code, choosing `ConcurrentHashMap`, `CopyOnWriteArrayList`, locking, or another ownership model is a broader concurrency decision. Concurrent collection iterators may use different contracts such as weak consistency, so the fail-fast rules of `ArrayList` do not apply to every collection.

## <a id="iterator-safe-mutation">Safe Mutation During Iteration</a>

If the current element needs to be removed during traversal, use the mutation operation supported by the iterator:

```java
List<Order> orders = new ArrayList<>(List.of(
        new Order(1, 1, 20_000),
        new Order(2, 2, 0),
        new Order(3, 3, 15_000)
));

Iterator<Order> iterator = orders.iterator();
while (iterator.hasNext()) {
    Order order = iterator.next();
    if (order.totalCents() == 0) {
        iterator.remove();
    }
}
```

`Iterator.remove()` ties the mutation to the current traversal state. Normally `next()` must be called first, and `remove()` cannot be called twice for the same element without another successful `next()`.

When the intent is simply “remove everything matching this predicate”, a higher-level API is clearer:

```java
orders.removeIf(order -> order.totalCents() == 0);
```

`ListIterator` has its own supported mutation operations such as `add`, `remove`, and `set`. For maps, code can iterate over `entrySet().iterator()` and call `iterator.remove()`.

The common rule is that mutation must go through a mechanism supported by the traversal protocol. If the requirement is “one thread iterates while another thread mutates”, that is a concurrent-collection/synchronization problem rather than a trick for avoiding CME.
