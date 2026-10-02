<a id="back-to-top"></a>

# MemorySegment

## Menu
- [What Does MemorySegment Represent?](#memory-segment-model)
- [Heap Segments and Native Segments](#heap-native-segments)
- [Mapped Segments and File-Backed Native Memory](#mapped-segments)
- [Spatial Bounds and Access Ranges](#segment-spatial-bounds)
- [Reading and Writing Through MemorySegment](#segment-access)
- [Slices and Views over the Same Memory Region](#segment-slicing)

## <a id="memory-segment-model">What Does MemorySegment Represent?</a>

<details>
<summary>Click for details</summary>

A `MemorySegment` models a **contiguous region of memory with explicit bounds and lifetime semantics**. It is more than a numeric address.

The segment tells Java:

- how large the region is;
- whether the backing storage is heap or native memory;
- which scope controls its validity;
- which threads may access it;
- whether the view is read-only.

This changes the mental model from raw-pointer programming:

```text
raw pointer
→ "an address; the programmer remembers the rest"

MemorySegment
→ "bounded region + scope + access constraints"
```

A segment can represent an entire allocation or only a slice of one. Several segments may refer to overlapping parts of the same backing region while exposing different bounds.

FFM validates segment accesses before touching memory, which is the basis for spatial and temporal safety.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap-native-segments">Heap Segments and Native Segments</a>

<details>
<summary>Click for details</summary>

Java 21 defines two broad kinds of memory segments.

**Heap segments** are backed by Java-heap storage. Factory methods such as `MemorySegment.ofArray(int[])` create a segment over an existing Java array:

```java
int[] values = {10, 20, 30};
MemorySegment heap = MemorySegment.ofArray(values);
```

**Native segments** are backed by off-heap memory and are commonly allocated through an `Arena`:

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment nativeMemory = arena.allocate(64);
}
```

The distinction matters because ownership and lifetime differ. Heap storage follows Java object reachability. Native allocation has a scope whose lifetime is controlled by the arena that produced it.

Thread-access semantics differ as well. Heap segments are accessible from any thread, while native segments follow the accessibility policy of their arena.

Use the same segment abstraction for reading and writing both kinds of memory, but do not assume their lifecycle or address capabilities are identical.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mapped-segments">Mapped Segments and File-Backed Native Memory</a>

<details>
<summary>Click for details</summary>

A mapped segment is a native memory segment backed by a **memory-mapped file region** rather than a freshly allocated anonymous native block.

Java 21 exposes an FFM preview overload on `FileChannel.map` that accepts an `Arena`:

```java
try (FileChannel channel = FileChannel.open(
        path,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE);
     Arena arena = Arena.ofConfined()) {

    MemorySegment mapped = channel.map(
        FileChannel.MapMode.READ_WRITE,
        0,
        channel.size(),
        arena
    );
}
```

The arena controls the mapped segment's scope. When that scope ends, the segment becomes invalid and the mapping is released according to the API contract.

Mapped segments also expose mapping-related operations such as `load()`, `isLoaded()`, `unload()`, and `force()` where supported.

The relationship is:

```text
native segment
└─ off-heap region

mapped segment
└─ native segment whose region is backed by a file mapping
```

Mapped memory is still subject to the same segment bounds, lifetime, and thread-access checks.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="segment-spatial-bounds">Spatial Bounds and Access Ranges</a>

<details>
<summary>Click for details</summary>

Every segment has a byte size. Access through that segment must stay entirely inside the represented range.

For a 16-byte segment, writing a four-byte integer at offset 12 is valid, while writing it at offset 14 would cross the end. The failing line uses `JAVA_INT_UNALIGNED` so the example isolates the bounds violation instead of also triggering an alignment violation:

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(16, ValueLayout.JAVA_INT.byteAlignment());

    segment.set(ValueLayout.JAVA_INT, 12, 99);                  // bytes 12..15
    // segment.set(ValueLayout.JAVA_INT_UNALIGNED, 14, 99);     // out of bounds
}
```

This is **spatial safety**. The bounds belong to the current segment view, not merely to some larger allocation that may exist underneath.

That matters for slices. If a 100-byte allocation is sliced to expose only 8 bytes, access through that slice is limited to those 8 bytes even though the backing allocation is larger.

Bounds checking protects accesses performed through Java's segment operations. It cannot stop arbitrary native code from misusing a pointer after Java passes that pointer across the boundary.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="segment-access">Reading and Writing Through MemorySegment</a>

<details>
<summary>Click for details</summary>

`MemorySegment` provides typed access operations. A `ValueLayout` specifies the size, alignment, byte order, and Java carrier expected for the value.

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(16, ValueLayout.JAVA_INT.byteAlignment());

    segment.set(ValueLayout.JAVA_INT, 0, 42);
    int value = segment.get(ValueLayout.JAVA_INT, 0);
}
```

For repeated homogeneous values, index-based methods express an element index rather than a raw byte offset:

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment values = arena.allocate(
        ValueLayout.JAVA_INT.byteSize() * 10,
        ValueLayout.JAVA_INT.byteAlignment()
    );

    for (int i = 0; i < 10; i++) {
        values.setAtIndex(ValueLayout.JAVA_INT, i, i * 10);
    }
}
```

An access succeeds only when the segment is alive, accessible from the current thread, large enough for the requested value, writable for writes, and compatible with the layout's alignment constraints.

Do not treat the layout argument as decorative metadata. It is part of the access contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="segment-slicing">Slices and Views over the Same Memory Region</a>

<details>
<summary>Click for details</summary>

A slice is another `MemorySegment` view over part of an existing segment's backing region. Creating a slice does not inherently copy the bytes.

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment block = arena.allocate(64);
    MemorySegment header = block.asSlice(0, 16);
    MemorySegment payload = block.asSlice(16, 48);
}
```

The views have different bounds:

```text
block   : 64 bytes
header  : first 16 bytes
payload : remaining 48 bytes
```

Overlapping views observe the same backing memory. A write through one segment can therefore be visible through another segment that covers the same bytes.

Slices retain the lifetime relationship of their backing region. A slice cannot remain usable after the relevant scope is no longer alive.

Bounded views are useful for expressing ownership and intent. Passing a small slice to code that only needs a header is safer and clearer than exposing the entire buffer.

Bounds answer **where** a segment may access memory, but not **how long** that access remains valid. The next chapter introduces Arena as the owner of native-allocation lifetime and thread-access policy.

</details>

- [Quay lại đầu trang](#back-to-top)
