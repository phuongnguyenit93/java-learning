# Enum-Specialized Collections: EnumSet and EnumMap

This chapter completes the **Choosing Implementations and Specialized Collections** milestone. When domain elements or keys come from an `enum`, the valid universe is already known and has a fixed declaration order. `EnumSet` and `EnumMap` exploit exactly that property, making intent clearer than general-purpose structures such as `HashSet` and `HashMap`.

The examples use an order status enum:

```java
enum OrderStatus {
    NEW,
    PAID,
    PACKING,
    SHIPPED,
    CANCELLED
}
```

## <a id="enumset-model">EnumSet Model</a>

`EnumSet<E extends Enum<E>>` is a `Set` specialized for elements of **one enum type**. The JDK describes its internal representation as a bit vector, allowing each enum constant to map compactly to a bit position.

```java
EnumSet<OrderStatus> active = EnumSet.of(
        OrderStatus.NEW,
        OrderStatus.PAID,
        OrderStatus.PACKING
);

boolean needsWork = active.contains(OrderStatus.PAID);
```

Its factories make domain intent explicit:

```java
EnumSet<OrderStatus> none = EnumSet.noneOf(OrderStatus.class);
EnumSet<OrderStatus> all = EnumSet.allOf(OrderStatus.class);
EnumSet<OrderStatus> shippingFlow =
        EnumSet.range(OrderStatus.PAID, OrderStatus.SHIPPED);
EnumSet<OrderStatus> inactive = EnumSet.complementOf(active);
```

`EnumSet` iterates in the enum's **natural order**, meaning declaration order rather than insertion order. It rejects `null` elements and is not synchronized.

A defined iteration order does not imply Java 21 `SequencedSet`: `EnumSet` has its own declaration-order iteration contract but does not implement `SequencedSet`. Do not infer first/last/reversed sequenced APIs merely from the fact that a collection has stable iteration order.

Its iterators are **weakly consistent**: they do not throw `ConcurrentModificationException` and may or may not reflect modifications made after iterator creation. This iterator contract is different from the fail-fast behavior described for collections such as `ArrayList`.

Because the enum universe is finite and known, `EnumSet` is a strong fit for flag/capability/status groups. If the business requires arbitrary insertion order, `LinkedHashSet` has the more appropriate contract.

## <a id="enummap-model">EnumMap Model</a>

`EnumMap<K extends Enum<K>, V>` is a `Map` specialized for enum keys. The JDK uses an array-oriented internal representation, taking advantage of the enum key space instead of a general-purpose hash-bucket structure.

```java
EnumMap<OrderStatus, Integer> counts =
        new EnumMap<>(OrderStatus.class);

counts.put(OrderStatus.NEW, 5);
counts.put(OrderStatus.PAID, 12);
counts.put(OrderStatus.SHIPPED, 8);

System.out.println(counts.get(OrderStatus.PAID)); // 12
```

Keys are iterated in enum declaration order, giving a stable and predictable encounter order:

```java
for (var entry : counts.entrySet()) {
    System.out.println(entry.getKey() + " = " + entry.getValue());
}
```

`EnumMap` rejects `null` keys but permits `null` values. If null values are stored, `get(key) == null` cannot distinguish “no mapping” from “mapping to null”; use `containsKey` when that distinction matters.

Like `EnumSet`, `EnumMap` has a defined encounter order but does not implement `SequencedMap`. `SequencedMap` standardizes first/last/reversed behavior for maps that implement that interface; it is not a prerequisite for a map to have a defined order.

Its collection-view iterators are also **weakly consistent**: they do not throw `ConcurrentModificationException` and may or may not show modifications made after iterator creation. `EnumMap` itself is not synchronized.

## <a id="enum-collection-benefits">Enum Collection Benefits and Trade-Offs</a>

Enum-specific collections provide three main benefits:

- **Clearer domain modeling:** the type signature says that only one enum universe is valid for the elements/keys.
- **Stable ordering:** traversal follows enum declaration order instead of hash encounter order.
- **Specialized representation:** bit vectors for `EnumSet` and array-oriented storage for `EnumMap` are typically compact and efficient for this domain.

For example, a status policy can be expressed directly:

```java
EnumSet<OrderStatus> cancellable =
        EnumSet.of(OrderStatus.NEW, OrderStatus.PAID);

EnumMap<OrderStatus, String> labels =
        new EnumMap<>(OrderStatus.class);
labels.put(OrderStatus.NEW, "Waiting for payment");
labels.put(OrderStatus.PAID, "Paid");
labels.put(OrderStatus.SHIPPED, "On the way");
```

The trade-off is that their ordering is tied to enum declaration order. If the business needs insertion order, access order, or an arbitrary comparator order, `LinkedHashMap`, `TreeMap`, `LinkedHashSet`, or `TreeSet` may be a better fit.

Do not choose `EnumSet` or `EnumMap` only because they are “faster”. Their biggest value is expressing that the domain has a closed, known set of elements/keys. Performance and memory efficiency are useful consequences of that specialized representation.
