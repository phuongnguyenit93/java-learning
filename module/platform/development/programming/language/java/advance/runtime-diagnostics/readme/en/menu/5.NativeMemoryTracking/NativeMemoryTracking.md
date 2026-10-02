<a id="back-to-top"></a>

# Native Memory Diagnostics with Native Memory Tracking

## Menu
- [When to Look Beyond the Java Heap](#native-memory-diagnostics-purpose)
- [What NMT Tracks and What It Does Not](#nmt-scope-and-limitations)
- [Enabling NMT and Choosing Summary or Detail](#enabling-nmt-and-tracking-levels)
- [Reserved Versus Committed Memory](#reserved-vs-committed)
- [Baselines, summary.diff, and detail.diff](#nmt-baseline-and-diff)
- [Analyzing Native-Memory Growth Trends](#native-memory-growth-analysis)
- [NMT Overhead, Limitations, and Pitfalls](#nmt-overhead-and-pitfalls)

## <a id="native-memory-diagnostics-purpose">When to Look Beyond the Java Heap</a>

<details>
<summary>Click for details</summary>

The Java heap is only part of a JVM process. When RSS/process memory grows while post-GC heap remains stable, or a container is OOM-killed before the Java heap reaches -Xmx, the investigation must include **native memory**.

Native memory can include:

- metaspace and class metadata;
- platform-thread stacks;
- JIT/code cache;
- GC internal structures;
- direct/native buffers;
- JNI or native libraries;
- memory mappings and other VM structures.

Native Memory Tracking (NMT) observes memory managed internally by HotSpot. It does not replace operating-system metrics and it does not account for all native allocations made by the application or third-party libraries.

Mental model:

~~~text
Process RSS / virtual memory
        ├─ Java heap
        ├─ HotSpot-managed native memory  ← much of this is visible to NMT
        └─ third-party / application native memory
~~~

NMT is especially useful for the question: “Which JVM-native category is using memory, and which category is growing over time?”

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nmt-scope-and-limitations">What NMT Tracks and What It Does Not</a>

<details>
<summary>Click for details</summary>

NMT tracks HotSpot internal memory usage across categories such as Java Heap, Class, Thread, Code, GC, Compiler, Internal, Symbol, and others depending on the JDK build.

Its scope matters:

- NMT records allocations instrumented by HotSpot;
- it does **not** track allocations made by third-party native code;
- it also does **not** track allocations made by Oracle JDK class libraries;
- it is not an operating-system RSS monitor.

Therefore:

~~~text
RSS grows
NMT grows similarly
→ a JVM-managed native category is a strong candidate

RSS grows
NMT stays roughly flat
→ investigate memory outside NMT, mmap/file cache, allocator behavior, or OS-level causes
~~~

“NMT is flat” does not prove that native memory is healthy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enabling-nmt-and-tracking-levels">Enabling NMT and Choosing Summary or Detail</a>

<details>
<summary>Click for details</summary>

NMT is **disabled by default** and must be enabled when the JVM starts:

~~~text
-XX:NativeMemoryTracking=summary
~~~

or:

~~~text
-XX:NativeMemoryTracking=detail
~~~

The levels serve different purposes:

- **summary** aggregates usage by subsystem/category and is often enough for trend analysis;
- **detail** adds call-site and virtual-memory-map information for deeper investigation at higher data/overhead cost.

Query a running JVM with:

~~~text
jcmd <pid> VM.native_memory summary
jcmd <pid> VM.native_memory detail
~~~

NMT can only be enabled with a JVM startup option. `jcmd` can **stop** NMT with `VM.native_memory shutdown`, but it cannot start or restart NMT in a running JVM. Once NMT has been shut down, enabling it again requires restarting the JVM with NMT configured at startup.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reserved-vs-committed">Reserved Versus Committed Memory</a>

<details>
<summary>Click for details</summary>

NMT output commonly shows two values that answer different questions.

**Reserved**  
Virtual address space set aside for a purpose. Reservation does not mean all of that space is backed by physical memory.

**Committed**  
The portion of reserved space committed for use by the VM. It is generally closer to memory the VM may actively consume, but it is still not identical to process RSS.

Conceptual example:

~~~text
Java Heap (reserved=4096MB, committed=1024MB)
~~~

The JVM has reserved a larger address range for possible heap growth while only part is currently committed.

Do not sum reserved values and compare them directly to RSS. Compare committed categories over time and correlate them with OS-level memory.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nmt-baseline-and-diff">Baselines, summary.diff, and detail.diff</a>

<details>
<summary>Click for details</summary>

NMT is most useful as a **trend** tool.

Create a baseline at a point that matches the diagnostic question:

~~~text
jcmd <pid> VM.native_memory baseline
~~~

Later compare:

~~~text
jcmd <pid> VM.native_memory summary.diff
jcmd <pid> VM.native_memory detail.diff
~~~

For steady-state growth analysis, a practical workflow is:

1. let the JVM reach a representative post-warm-up state;
2. create a baseline;
3. wait through a meaningful workload/time window;
4. inspect summary.diff;
5. move to detail or another evidence source only for suspicious categories.

A startup baseline can be useful when the investigation needs to measure growth from early JVM life, while a post-warm-up baseline is usually easier to interpret for steady-state leak analysis. Choose the baseline according to the period you intend to compare rather than treating one timing as universally correct.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-memory-growth-analysis">Analyzing Native-Memory Growth Trends</a>

<details>
<summary>Click for details</summary>

When native memory grows, ask **which category grows and whether that growth matches workload behavior**.

Examples:

- **Thread** grows with the number of platform threads → inspect thread creation and stack sizing;
- **Class** grows with dynamic loading → inspect class-loader lifecycle;
- **Code** grows during warm-up → JIT/code-cache activity may explain it;
- **GC/Internal** grows → deeper JVM interpretation may be needed;
- NMT stays flat while RSS grows → memory outside HotSpot tracking becomes more likely.

Useful reasoning:

~~~text
OS RSS trend
   + NMT summary.diff
        ↓
identify the growing category
        ↓
correlate with thread/class/JFR/application behavior
        ↓
choose JVM, native, or application handoff
~~~

NMT narrows the search space; it rarely names the final leaking function or object by itself.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nmt-overhead-and-pitfalls">NMT Overhead, Limitations, and Pitfalls</a>

<details>
<summary>Click for details</summary>

NMT is not free. Oracle documents an approximately **5–10% performance overhead** when tracking is enabled; NMT also adds tracking metadata to allocations it observes. Production use should therefore be intentional.

Common mistakes include:

- trying to enable NMT after the incident has already begun;
- confusing reserved with committed;
- expecting NMT totals to equal RSS;
- diagnosing third-party native leaks from a mechanism that does not fully track them;
- treating one snapshot as proof of a leak;
- enabling detail everywhere without a question that requires it.

NMT is a HotSpot diagnostic feature, not a Java-language abstraction. Hand off to JVM internals for deeper VM-category interpretation and to Native Interoperability when JNI/FFM or third-party native allocation becomes the likely source.

</details>

- [Quay lại đầu trang](#back-to-top)
