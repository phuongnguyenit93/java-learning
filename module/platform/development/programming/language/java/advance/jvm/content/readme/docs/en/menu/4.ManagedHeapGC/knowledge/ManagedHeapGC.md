<a id="back-to-top"></a>

# Managed Heap, Object Allocation, and Garbage Collection

## Menu
- [What Problem Does the Managed Heap Solve?](#managed-heap-purpose)
- [The Heap Object-Allocation Model](#object-allocation-model)
- [Object Layout - Specification vs Implementation Detail](#object-layout-boundary)
- [Reachability and Object Liveness](#reachability-and-liveness)
- [Automatic Memory Reclamation](#automatic-memory-reclamation)
- [The Garbage-Collection Model](#garbage-collection-model)
- [Collector Strategies and How They Differ](#collector-strategies)
- [Throughput, Latency, and Memory-Footprint Trade-Offs](#gc-goals-and-tradeoffs)
- [Reference Strengths and Garbage-Collector Interaction](#reference-strengths)
- [Garbage-Collection Mechanics vs Diagnostics](#gc-mechanism-vs-diagnostics)

## <a id="managed-heap-purpose">What Problem Does the Managed Heap Solve?</a>

<details>
<summary>Click for details</summary>

Applications continuously create objects with very different lifetimes. If every piece of Java code had to determine exactly when to free each object, complex ownership would make use-after-free, double-free, and manual-memory leaks much easier to create.

The managed heap changes the responsibility split:

```text
application
→ create and use objects

runtime / GC
→ determine what remains reachable
→ reclaim storage when appropriate
```

Java developers still manage **logical lifetime** through references and explicit resource lifecycles, but they do not directly free ordinary heap objects.

The trade-off is that the runtime must track reachability and perform collection work, introducing throughput, latency, and memory-footprint trade-offs.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-allocation-model">The Heap Object-Allocation Model</a>

<details>
<summary>Click for details</summary>

At specification level, objects and arrays receive storage from the heap. Source code such as:

```java
Order order = new Order();
```

supports this conceptual model:

```text
new instruction
→ ensure the class is ready according to lifecycle rules
→ allocate storage for an uninitialized object
→ place the object reference into the operand-stack flow
→ invokespecial calls constructor <init>
→ constructor execution completes initialization
```

At bytecode level, `new` and constructor invocation are **separate steps**. `new` creates an uninitialized object; compilers commonly emit `dup` followed by `invokespecial ... <init>` to invoke the constructor. That distinction matters when reading bytecode and understanding verifier rules.

HotSpot may use fast allocation techniques such as bump-pointer allocation or thread-local allocation buffers depending on collector/layout. Those are implementation details, not JVM contract.

Do not teach “every Java object is allocated through a TLAB” as a portable rule.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-layout-boundary">Object Layout - Specification vs Implementation Detail</a>

<details>
<summary>Click for details</summary>

The JVMS intentionally does not prescribe the physical representation of objects. A JVM implementation can choose headers, alignment, field placement, compressed references, and other details as long as observable semantics remain correct.

HotSpot commonly uses object headers and alignment rules, but exact sizes depend on JVM version, architecture, and configuration.

Therefore a claim such as:

```text
every Java object = X-byte header + fields
```

is only meaningful inside a specific implementation context.

For precise memory-sizing discussions, state the JVM/version, architecture, compressed-reference settings, and alignment assumptions.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reachability-and-liveness">Reachability and Object Liveness</a>

<details>
<summary>Click for details</summary>

Garbage collection cannot decide object lifetime simply by asking “how old is this object?” The more important question is whether an object remains **reachable from runtime roots through the reference graph**.

```text
GC roots
  ↓
reachable object graph
  ↓
objects that must remain live

unreachable objects
  ↓
storage can eventually be reclaimed
```

A managed-memory leak often happens because application logic accidentally retains a strong reference, so an object remains reachable even though the business logic no longer needs it.

Reachability is therefore where application reference design meets garbage-collector behavior.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="automatic-memory-reclamation">Automatic Memory Reclamation</a>

<details>
<summary>Click for details</summary>

Automatic reclamation means application code does not call `free(object)` for ordinary heap objects. The collector identifies storage that can be reused after objects are no longer part of the live graph according to collector semantics.

“Automatic” does **not** mean:

- GC runs immediately when the last strong reference disappears;
- reclamation occurs at a deterministic time;
- memory is immediately returned to the operating system;
- external resources such as files or sockets close themselves on time.

Keep this distinction:

```text
heap-object lifetime
≠ external-resource lifetime
```

External resources still need deterministic lifecycle management such as `try-with-resources`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collection-model">The Garbage-Collection Model</a>

<details>
<summary>Click for details</summary>

A garbage collector is the storage-management subsystem that reclaims heap space while preserving Java-level program semantics.

A conceptual collector must solve problems such as:

1. identify live/reachable data;
2. identify and reclaim garbage;
3. possibly move/compact objects;
4. update references when objects move;
5. coordinate safely with application threads.

The specification does not require one collector or one algorithm. A collector may perform more stop-the-world work, more concurrent work, use regions, compact aggressively, or use a different organization.

Learn the **problem and trade-offs** before memorizing collector product names.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collector-strategies">Collector Strategies and How They Differ</a>

<details>
<summary>Click for details</summary>

Collectors differ mainly in heap organization and in how collection work is divided between application and GC threads.

Useful dimensions include:

- **serial vs parallel** collection work;
- **stop-the-world vs concurrent** phases;
- **generational vs non-generational** organization;
- **region-based** heap organization;
- **compacting vs non-compacting** strategies.

Java 21 provides collectors with very different goals, and ZGC can even operate in generational or non-generational modes. That is a concrete reminder that there is no single algorithm called “Java GC.”

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gc-goals-and-tradeoffs">Throughput, Latency, and Memory-Footprint Trade-Offs</a>

<details>
<summary>Click for details</summary>

Three goals frequently compete:

**Throughput**
Maximize the fraction of CPU time spent running application work rather than collection.

**Latency / pause time**
Keep individual application pauses short enough for response-time requirements.

**Memory footprint**
Avoid retaining excessive memory headroom.

No collector/configuration optimizes all three perfectly. More heap headroom may reduce collection frequency but increase footprint; a highly concurrent collector may reduce pauses while using extra CPU/resources.

The right question is not “which collector is best?” but:

```text
What does this workload prioritize,
and what SLO/resource constraints matter?
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-strengths">Reference Strengths and Garbage-Collector Interaction</a>

<details>
<summary>Click for details</summary>

In addition to ordinary strong references, Java provides `SoftReference`, `WeakReference`, and `PhantomReference` for specialized coordination with reachability and garbage collection.

- **Strong** references keep objects strongly reachable through the ordinary object graph.
- **Soft** references may be cleared under memory pressure; they are historically associated with memory-sensitive caches but are not a precise cache policy.
- **Weak** references do not keep a referent alive solely because the weak reference exists; they fit weak ownership/canonicalization use cases.
- **Phantom** references support post-mortem lifecycle/cleanup coordination; `get()` always returns `null`.

Reference objects cooperate closely with the garbage collector. They are advanced tools; ordinary ownership is usually clearer with strong references plus explicit lifecycle management.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gc-mechanism-vs-diagnostics">Garbage-Collection Mechanics vs Diagnostics</a>

<details>
<summary>Click for details</summary>

The JVM module explains **why garbage collection exists and how collector trade-offs work**. Production questions such as long pauses, allocation pressure, or suspected leaks require evidence.

```text
JVM
→ allocation / reachability / collector concepts / goals

Runtime Diagnostics
→ GC logging
→ heap histograms / heap dumps
→ JFR allocation events
→ evidence-driven troubleshooting
```

Do not tune from theory alone. The same symptom can come from allocation rate, live-set size, heap sizing, collector behavior, or a completely unrelated subsystem.

Theory creates hypotheses; diagnostics validates them.

</details>

- [Quay lại đầu trang](#back-to-top)
