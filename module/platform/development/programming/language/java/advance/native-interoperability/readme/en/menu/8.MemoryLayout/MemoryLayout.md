<a id="back-to-top"></a>

# MemoryLayout and Native Data Representation

## Menu
- [Why Does Native Data Need MemoryLayout?](#memory-layout-purpose)
- [ValueLayout and Primitive Carriers](#value-layouts)
- [Struct Layouts](#struct-layout)
- [Sequence Layouts](#sequence-layout)
- [Union Layouts](#union-layout)
- [AddressLayout and Pointer-Shaped Data](#address-layout)
- [Alignment, Size, and Offset](#layout-alignment-offset)
- [Layout Paths and Structured Memory Access](#layout-path-access)

## <a id="memory-layout-purpose">Why Does Native Data Need MemoryLayout?</a>

<details>
<summary>Click for details</summary>

A native function does not receive Java objects with Java field metadata. It receives bytes whose meaning is defined by a native data contract: value width, byte order, alignment, member order, padding, arrays, unions, and pointer-sized values.

`MemoryLayout` is FFM's way to describe that contract in Java.

```text
MemorySegment
→ where the bytes are

MemoryLayout
→ how those bytes are structured and interpreted
```

This distinction matters because Java object layout is not a portable representation of a C struct or another native data structure. Even fields with similar source-language names may have different size or alignment rules.

Layouts are immutable descriptions. They can report `byteSize()` and `byteAlignment()`, carry optional names, form nested structures, calculate offsets, and derive structured access handles.

The layout must match the native ABI contract. A syntactically valid Java layout that describes the wrong native structure is still an interoperability bug.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-layouts">ValueLayout and Primitive Carriers</a>

<details>
<summary>Click for details</summary>

A `ValueLayout` describes one scalar value in memory. It combines a byte size and alignment with a Java carrier type and byte order.

Java 21 provides common constants such as:

- `ValueLayout.JAVA_BYTE`
- `ValueLayout.JAVA_SHORT`
- `ValueLayout.JAVA_INT`
- `ValueLayout.JAVA_LONG`
- `ValueLayout.JAVA_FLOAT`
- `ValueLayout.JAVA_DOUBLE`
- `ValueLayout.ADDRESS` for pointer-shaped values

A value layout can be used directly with `MemorySegment.get` and `set`:

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT);

    segment.set(ValueLayout.JAVA_INT, 0, 123);
    int value = segment.get(ValueLayout.JAVA_INT, 0);
}
```

The **carrier** is the Java type used by the access operation. For example, `JAVA_INT` uses `int`, while an `AddressLayout` uses `MemorySegment` as its carrier.

Do not assume that a C type always maps to the Java layout with the same familiar name. C type widths can depend on the platform ABI.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="struct-layout">Struct Layouts</a>

<details>
<summary>Click for details</summary>

A `StructLayout` models heterogeneous members placed **one after another** in memory.

For a native structure such as:

```c
struct Point {
    int x;
    int y;
};
```

a corresponding FFM description can be:

```java
StructLayout POINT = MemoryLayout.structLayout(
    ValueLayout.JAVA_INT.withName("x"),
    ValueLayout.JAVA_INT.withName("y")
);
```

Names are useful because later layout paths can select members by name instead of hard-coded byte offsets.

Real native structs frequently contain padding required by an ABI. `structLayout` describes the member sequence you provide; when padding is part of the native representation, model it explicitly with `MemoryLayout.paddingLayout(...)` or otherwise construct the layout according to the target ABI.

The important rule is that the Java layout must reproduce the native structure's **actual binary representation**, not just its list of source-level field names.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sequence-layout">Sequence Layouts</a>

<details>
<summary>Click for details</summary>

A `SequenceLayout` models a homogeneous repetition of one element layout. It is the natural layout for fixed-size native arrays and repeated records.

```java
SequenceLayout TEN_INTS =
    MemoryLayout.sequenceLayout(10, ValueLayout.JAVA_INT);
```

Conceptually:

```text
sequenceLayout(4, JAVA_INT)

element 0 | element 1 | element 2 | element 3
   int    |    int    |    int    |    int
```

The sequence has an element count and an element layout. Its total byte size follows from repeating that element representation.

Sequence layouts can be nested to represent multidimensional data, or combined with struct layouts to describe arrays of records.

Use a sequence when the native contract says “repeat this same representation N times.” If members differ in type or meaning, a struct or union is the more appropriate model.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="union-layout">Union Layouts</a>

<details>
<summary>Click for details</summary>

A `UnionLayout` models heterogeneous members that all begin at the **same starting offset**. This matches the overlapping storage semantics of a native union.

For example:

```c
union Value {
    int   i;
    float f;
};
```

can be described as:

```java
UnionLayout VALUE = MemoryLayout.unionLayout(
    ValueLayout.JAVA_INT.withName("i"),
    ValueLayout.JAVA_FLOAT.withName("f")
);
```

Unlike a struct, the members do not follow one another:

```text
struct → member A | member B | member C
union  → member A
         member B    all overlay the same starting bytes
         member C
```

The layout tells Java how the storage is shaped; it does not know which union member is logically active. If a native API uses a tag/discriminator to say which interpretation is valid, application code must honor that protocol.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="address-layout">AddressLayout and Pointer-Shaped Data</a>

<details>
<summary>Click for details</summary>

An `AddressLayout` models a machine address stored in memory. Its Java carrier is `MemorySegment`, and its size/alignment depend on the platform's address representation.

The standard `ValueLayout.ADDRESS` constant represents a native address value:

```java
AddressLayout pointer = ValueLayout.ADDRESS;
```

An address layout can optionally have a **target layout** that describes the memory expected to exist at the stored address. That makes pointer relationships explicit in a larger structured layout.

This still requires caution. A pointer-sized value does not prove that the target memory is valid, large enough, alive, or owned by Java.

```text
address value
    ↓
points somewhere
    ↓
target layout says how Java intends to interpret that memory
    ↓
lifetime/ownership must still be correct
```

Some address/target-layout operations are restricted in Java 21 because incorrect pointer interpretation can escape normal memory-safety guarantees.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layout-alignment-offset">Alignment, Size, and Offset</a>

<details>
<summary>Click for details</summary>

Three layout properties are fundamental to ABI correctness:

- **size**: how many bytes the layout occupies;
- **alignment**: which byte boundaries are valid for correctly aligned access;
- **offset**: where a selected member begins relative to the enclosing layout.

`MemoryLayout.byteSize()` and `byteAlignment()` expose the first two. Layout paths and `byteOffset(...)` can calculate member offsets.

Consider:

```java
StructLayout RECORD = MemoryLayout.structLayout(
    ValueLayout.JAVA_BYTE.withName("kind"),
    MemoryLayout.paddingLayout(3),
    ValueLayout.JAVA_INT.withName("value")
);
```

The padding moves `value` to the intended offset. Without matching the native structure's padding/alignment rules, Java can read the correct number of bytes from the wrong location.

Alignment is also enforced during access. A layout can require a stricter aligned address than an arbitrary byte offset provides.

Never guess offsets from source code appearance. Derive them from the real native ABI and encode that representation in the layout.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layout-path-access">Layout Paths and Structured Memory Access</a>

<details>
<summary>Click for details</summary>

Layout paths provide a structured way to navigate nested layouts. Instead of manually adding byte offsets, code selects members or sequence elements from the layout tree.

Example:

```java
StructLayout POINT = MemoryLayout.structLayout(
    ValueLayout.JAVA_INT.withName("x"),
    ValueLayout.JAVA_INT.withName("y")
);

long yOffset = POINT.byteOffset(
    MemoryLayout.PathElement.groupElement("y")
);
```

Paths can describe group members, fixed or open sequence elements, and—in address-layout cases—dereference steps where the API permits them.

The same path model supports operations such as:

- `byteOffset(...)` to compute a fixed offset;
- `select(...)` to obtain a nested layout;
- `sliceHandle(...)` to produce slices for selected regions;
- `varHandle(...)` to obtain structured memory access.

This chapter uses those APIs only as FFM layout tools. The deeper mechanics of `VarHandle` and `MethodHandle` belong to Dynamic Runtime.

Prefer layout paths when the native structure is nested or reused. They keep the access logic connected to the declared data shape and reduce duplicated magic offsets.

Layouts make native data contracts explicit; foreign-function calls need the same discipline for function signatures. The next chapter connects layouts with SymbolLookup, FunctionDescriptor, and Linker so Java can resolve and describe a callable native entry point.

</details>

- [Quay lại đầu trang](#back-to-top)
