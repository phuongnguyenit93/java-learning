<a id="back-to-top"></a>

# Arena, Ownership, and Lifetime

## Menu
- [What Is Arena For?](#arena-purpose)
- [Arena and Native-Memory Lifetime](#arena-lifetime)
- [Global, Automatic, Confined, and Shared Arenas](#arena-kinds)
- [Allocation and Timely Deallocation](#arena-allocation)
- [Temporal Safety and Segment Validity](#temporal-safety)
- [Thread Access and Confinement](#arena-thread-access)

## <a id="arena-purpose">What Is Arena For?</a>

<details>
<summary>Click for details</summary>

An `Arena` is the FFM abstraction that combines **native-memory allocation with lifetime and thread-access policy**.

The core problem is that off-heap memory is not reclaimed merely because a Java variable stops referencing it. Java therefore needs an explicit object that says:

- where new native allocations come from;
- how long those allocations remain valid;
- when their backing memory can be released;
- which threads may access segments associated with that lifetime.

```text
Arena
  ├─ allocates native memory
  ├─ owns a Scope
  ├─ defines when that scope is alive
  └─ defines thread accessibility
```

Every native segment allocated by an arena is associated with the arena's scope. Closing a closeable arena ends that lifetime and invalidates the segments tied to it.

Think of the arena as the lifetime owner, while `MemorySegment` is a bounded view used to access the memory.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arena-lifetime">Arena and Native-Memory Lifetime</a>

<details>
<summary>Click for details</summary>

An arena's most important job is to define the temporal boundary of native memory.

With a confined arena:

```java
MemorySegment segment;

try (Arena arena = Arena.ofConfined()) {
    segment = arena.allocate(32, ValueLayout.JAVA_INT.byteAlignment());
    segment.set(ValueLayout.JAVA_INT, 0, 7);
}

// arena is closed here; segment is no longer valid
```

Closing the arena releases the native regions it owns and makes associated segments unusable. Subsequent access through those segments fails rather than touching memory that has already been deallocated.

This relationship is deliberate:

```text
arena lifetime
    ↓
scope lifetime
    ↓
segment validity
    ↓
legal memory access
```

Do not model a segment's lifetime from the Java variable that stores it. A reference can still exist after the arena has closed; the scope, not variable reachability, determines whether access remains legal.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arena-kinds">Global, Automatic, Confined, and Shared Arenas</a>

<details>
<summary>Click for details</summary>

Java 21 provides four standard arena kinds with different lifetime and thread-access behavior.

| Arena | Lifetime | Explicitly closeable | Multi-thread access |
| --- | --- | --- | --- |
| `Arena.global()` | unbounded | no | yes |
| `Arena.ofAuto()` | GC-managed bounded lifetime | no | yes |
| `Arena.ofConfined()` | manually bounded | yes | no; owner thread only |
| `Arena.ofShared()` | manually bounded | yes | yes |

**Global** is convenient for data intended to live for the process lifetime, but its backing memory is never explicitly reclaimed through the arena.

**Automatic** allows the runtime to reclaim memory after the arena and its segments become unreachable, but deallocation timing is unspecified.

**Confined** gives deterministic cleanup and cheap single-thread ownership. It is usually the clearest choice for short-lived native work performed by one thread.

**Shared** also gives deterministic cleanup while allowing access from multiple threads, at the cost of a broader concurrency responsibility for the application.

Choose based on required lifetime and sharing, not convenience alone.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arena-allocation">Allocation and Timely Deallocation</a>

<details>
<summary>Click for details</summary>

Because `Arena` is also a `SegmentAllocator`, it provides allocation methods that return native `MemorySegment` instances associated with the arena's scope.

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment bytes = arena.allocate(128);
    MemorySegment ints = arena.allocate(
        ValueLayout.JAVA_INT.byteSize() * 10,
        ValueLayout.JAVA_INT.byteAlignment()
    );
}
```

The advantage of a closeable arena is **timely deallocation**. Try-with-resources makes the native lifetime visible in ordinary Java control flow:

```text
enter block
   ↓
allocate native resources
   ↓
use segments
   ↓
leave block
   ↓
arena.close()
   ↓
memory released + segments invalidated
```

This is preferable when the application knows exactly when a native working set is no longer needed.

Automatic and global arenas are useful for different lifetimes, but they give up deterministic reclamation. Use the narrowest lifetime that matches the native contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="temporal-safety">Temporal Safety and Segment Validity</a>

<details>
<summary>Click for details</summary>

Temporal safety prevents Java code from accessing a memory region after its lifetime has ended.

Every segment has an associated scope. Before an access proceeds, the API checks that the scope is still alive. If a closeable arena has already been closed, access through one of its segments fails with `IllegalStateException`.

```java
MemorySegment segment;

try (Arena arena = Arena.ofConfined()) {
    segment = arena.allocate(ValueLayout.JAVA_LONG);
}

// IllegalStateException: the backing scope is no longer alive
// segment.get(ValueLayout.JAVA_LONG, 0);
```

This is the managed answer to a classic native bug:

```text
native allocation freed
        ↓
old pointer retained
        ↓
use-after-free
        ↓
undefined behavior / corruption / crash
```

FFM prevents that class of mistake for accesses performed through the segment abstraction. It cannot prevent foreign native code from retaining and later dereferencing an address after Java has ended the corresponding lifetime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arena-thread-access">Thread Access and Confinement</a>

<details>
<summary>Click for details</summary>

Arena choice also determines thread accessibility.

A segment allocated from `Arena.ofConfined()` can only be accessed by the thread that created the arena. The same owner-thread restriction applies to closing that confined arena.

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(16);
    // use segment on this owner thread
}
```

If multiple threads must legally access the segment, use an arena whose policy allows it, such as `Arena.ofShared()`, `Arena.ofAuto()`, or `Arena.global()`.

Thread accessibility and thread safety are not the same thing:

```text
shared arena
→ FFM allows multiple threads to access the segment

application synchronization
→ still needed when those threads race on mutable native data
```

FFM enforces whether a thread is permitted to access the segment. It does not automatically make the data structure stored in that memory safe for concurrent mutation.

Once a memory region has explicit bounds, lifetime, and thread accessibility, the next question is what its bytes mean. The next chapter uses MemoryLayout to make native size, alignment, field placement, and pointer-shaped data explicit.

</details>

- [Quay lại đầu trang](#back-to-top)
