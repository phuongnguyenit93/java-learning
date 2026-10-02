<a id="back-to-top"></a>

# Heap and Garbage Collection Diagnostics

## Menu
- [What Heap and GC Diagnostics Solves](#heap-gc-diagnostics-purpose)
- [Heap Usage, Allocation Pressure, and Memory Pressure](#heap-usage-vs-allocation-pressure)
- [Class Histograms and Their Proper Use](#class-histogram)
- [Analyzing Heap Dumps for OutOfMemoryError and Memory Growth](#heap-dump-and-oome)
- [GC Logs, GC Events, and Runtime Evidence](#gc-logs-and-events)
- [Snapshots Versus Memory Trends Over Time](#snapshot-vs-trend)
- [From a Suspected Memory Leak to Testable Evidence](#memory-leak-evidence)
- [Common Heap and GC Diagnostic Pitfalls](#heap-gc-diagnostic-pitfalls)

## <a id="heap-gc-diagnostics-purpose">What Heap and GC Diagnostics Solves</a>

<details>
<summary>Click for details</summary>

Heap/GC diagnostics answers questions such as:

- why does heap usage keep growing?
- is an OutOfMemoryError caused by heap pressure or something else?
- is allocation rate unusually high?
- is GC running too frequently?
- are objects being retained longer than expected?

Keep **JVM mechanics** separate from **diagnostic evidence**. The JVM module owns heap layout, collectors, and allocation mechanics; this chapter focuses on using histograms, heap dumps, GC logs, and JFR to test hypotheses.

~~~text
memory symptom
   ↓
heap usage + GC behavior
   ↓
histogram / heap dump / JFR
   ↓
retention or allocation hypothesis
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap-usage-vs-allocation-pressure">Heap Usage, Allocation Pressure, and Memory Pressure</a>

<details>
<summary>Click for details</summary>

Three ideas are often confused.

**Heap usage**  
How much Java heap is occupied at a point in time.

**Allocation pressure**  
How quickly new objects are created. High allocation can cause frequent GC without implying a leak.

**Memory pressure**  
Insufficient headroom in heap, native memory, or the OS/container limit.

Example:

~~~text
high allocation rate
GC reclaims successfully
post-GC heap stays stable
→ no leak evidence yet

post-GC heap rises over time
→ investigate retention
~~~

Trend and post-GC usage usually tell more than one peak value.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-histogram">Class Histograms and Their Proper Use</a>

<details>
<summary>Click for details</summary>

A class histogram reports instance count and total size grouped by class.

With jcmd:

~~~text
jcmd <pid> GC.class_histogram
~~~

Do not treat the word “histogram” as meaning “cheap.” In Java 21, `jcmd` documents `GC.class_histogram` as a **High impact** command whose cost depends on heap size and content. Before collecting it—especially before taking several histograms for comparison—check the target JVM, symptom window, and operational impact budget.

Histograms are useful for:

- finding classes with large instance counts or byte totals;
- comparing snapshots to identify growing populations;
- deciding whether a heap dump is justified.

A histogram does not show the full object graph or retention paths. A large class is not automatically leaking; it may represent a valid cache or current working set.

Useful pattern:

~~~text
histogram t1
   ↓
histogram t2
   ↓
which class population grows?
   ↓
heap dump / JFR / application context
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap-dump-and-oome">Analyzing Heap Dumps for OutOfMemoryError and Memory Growth</a>

<details>
<summary>Click for details</summary>

A heap dump is a snapshot of the Java-heap object graph. It is useful when the investigation needs to know **which objects remain live and what keeps them reachable**.

Create one from a running JVM with:

~~~text
jcmd <pid> GC.heap_dump heap.hprof
~~~

Analysis commonly looks at:

- retained size;
- dominators;
- GC roots;
- reference chains;
- class-loader retention;
- unexpectedly large caches or collections.

OutOfMemoryError does not automatically mean a leak. The heap may simply be undersized, workload may have grown, there may be an allocation burst, the live data set may legitimately be too large, or there may be real retention.

Heap dumps can be expensive in pause time and disk usage. The Postmortem chapter owns automatic capture on OOME; this chapter owns **analysis**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gc-logs-and-events">GC Logs, GC Events, and Runtime Evidence</a>

<details>
<summary>Click for details</summary>

GC logs provide a timeline of collection activity. With unified logging:

~~~text
-Xlog:gc*
~~~

GC evidence helps answer:

- how often collections run;
- whether pause time is changing;
- how heap size changes before and after collection;
- whether collections successfully reclaim memory.

Reasoning example:

~~~text
GC runs constantly
post-GC heap still rises
→ retention becomes more plausible

GC runs frequently
post-GC heap stays stable
→ allocation pressure or heap sizing may be more relevant
~~~

JFR GC events add timeline correlation with CPU, threads, and I/O.

When interpretation requires collector internals, hand off to the JVM curriculum rather than duplicating it here.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="snapshot-vs-trend">Snapshots Versus Memory Trends Over Time</a>

<details>
<summary>Click for details</summary>

One large snapshot does not prove a leak. A leak is a **retention-over-time** problem.

Example:

~~~text
t1 post-GC heap = 500 MB
t2 post-GC heap = 700 MB
t3 post-GC heap = 950 MB
~~~

If workload is comparable and the live set grows steadily, further investigation is justified.

Trend evidence can come from:

- GC logs;
- JMX/MXBeans;
- JFR;
- repeated histograms;
- monitoring metrics.

Those sources do not have equal collection cost. In particular, repeated `GC.class_histogram` calls still repeat a command documented as **High impact** depending on heap size/content. Use repeated histograms only when their diagnostic value justifies that cost; otherwise prefer lower-impact time-series evidence such as existing GC logs, JFR, management data, or monitoring metrics.

A heap dump gives detailed object structure but only at one point in time. Multiple dumps may provide stronger evidence but at higher operational cost.

Always correlate with workload, deployments, and traffic. Legitimate data growth is not a leak.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-leak-evidence">From a Suspected Memory Leak to Testable Evidence</a>

<details>
<summary>Click for details</summary>

A memory-leak hypothesis can be built as a chain:

~~~text
symptom: memory grows
   ↓
does post-GC live set grow?
   ↓
which histogram populations grow?
   ↓
what retains those objects in the heap graph?
   ↓
which allocation or old-object evidence points to the code path?
~~~

Strong evidence often includes:

- object populations increasing over time;
- retained paths leading to a long-lived root;
- growth that workload does not explain;
- a live set that does not fall as expected after collection.

Do not blame class X merely because many X instances exist. The real issue may be a cache owner, listener, or other long-lived structure retaining them.

If growth is outside the Java heap, move to NMT/native-memory diagnostics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap-gc-diagnostic-pitfalls">Common Heap and GC Diagnostic Pitfalls</a>

<details>
<summary>Click for details</summary>

Common mistakes include:

**High heap usage means a leak**  
A healthy JVM may use much of its heap if GC reclaims enough memory.

**High allocation rate means a leak**  
Allocation and retention are different.

**One heap dump equals root cause**  
A dump needs trend, workload, and runtime context.

**The top histogram class is the culprit**  
It may simply be valid application data.

**Looking only at pre-GC used memory**  
Post-GC live-set trends are often more useful for retention.

**Ignoring native memory**  
Growing process RSS does not necessarily come from Java heap.

**Treating heap dumps as risk-free**  
They can pause the JVM, consume large disk space, and contain sensitive data.

Good heap/GC diagnostics combines snapshots, trends, and runtime context.

</details>

- [Quay lại đầu trang](#back-to-top)
