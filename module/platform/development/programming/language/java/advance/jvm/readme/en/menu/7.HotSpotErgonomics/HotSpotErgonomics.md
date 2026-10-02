<a id="back-to-top"></a>

# HotSpot Ergonomics and JVM Configuration Boundaries

## Menu
- [What Problem Does Ergonomics Solve?](#ergonomics-purpose)
- [How HotSpot Chooses Default Runtime Policies](#hotspot-default-selection)
- [Heap-Sizing Concepts](#heap-sizing-concepts)
- [Collector Selection and GC Goals](#collector-selection-and-goals)
- [Runtime-Compiler Policy](#runtime-compiler-policy)
- [The VM Options Model](#vm-options-model)
- [Portable Behavior vs Implementation-Specific Controls](#portable-vs-implementation-specific-controls)
- [When to Stop Tuning and Move to Diagnostics](#configuration-vs-diagnostics)

## <a id="ergonomics-purpose">What Problem Does Ergonomics Solve?</a>

<details>
<summary>Click for details</summary>

A JVM implementation must run across many machines and workloads. If every application had to choose its collector, heap sizing, and compiler policy manually before it could run well, configuration would be both complex and fragile.

**Ergonomics** is the HotSpot idea of selecting defaults and using heuristics based on platform characteristics, available resources, and runtime behavior.

```text
machine + JVM version + workload signals
        ↓
ergonomic defaults / adaptive heuristics
        ↓
runtime behavior
```

Ergonomics does not mean the defaults are optimal for every SLO. It provides a sensible baseline before evidence justifies tuning.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotspot-default-selection">How HotSpot Chooses Default Runtime Policies</a>

<details>
<summary>Click for details</summary>

HotSpot can choose defaults for the garbage collector, initial/maximum heap sizing, and runtime compiler policy using environment and JVM-version rules.

Java 21 documentation describes defaults in terms of server-class machines and resource heuristics. These values are **version/platform-specific implementation behavior**, not JVM Specification guarantees.

For example, the Java 21 HotSpot tuning guide describes G1 as the default collector on server-class machines, Serial GC for other configurations, and tiered compilation using C1 and C2 as the default compiler policy. These are useful examples of ergonomics selecting runtime policy, not permanent rules for every future JDK.

For production reasoning, record:

```text
JDK vendor/version
host/container resources
explicit VM flags
effective defaults
```

instead of relying on a statement such as “the JVM always defaults to X.”

Upgrading the JDK can change defaults and heuristics even when application code is unchanged.

To inspect effective HotSpot flags on the exact runtime you are using, a practical observation is:

```text
java -XX:+PrintFlagsFinal -version
```

Treat that output as evidence about the **current runtime**, not as part of the JVM specification.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap-sizing-concepts">Heap-Sizing Concepts</a>

<details>
<summary>Click for details</summary>

Heap sizing controls how much memory the managed heap can use. In the Java 21 launcher, `-Xms` sets the **minimum and initial heap size**, while `-Xmx` sets the **maximum heap size**.

Trade-offs include:

- too little heap → more frequent collection and allocation pressure;
- more heap → more headroom, but a larger footprint and potentially different pause behavior;
- `-Xmx` limits the heap, **not the entire JVM process**.

Inside a container, think in terms of:

```text
container memory limit
> heap
+ thread stacks
+ class metadata
+ code cache
+ direct/native memory
+ native libraries/runtime overhead
```

Giving nearly the whole container limit to the Java heap leaves no room for the rest of the process.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collector-selection-and-goals">Collector Selection and GC Goals</a>

<details>
<summary>Click for details</summary>

Collector selection should begin with workload requirements, not personal preference.

Ask:

- Are latency SLOs strict?
- Is throughput more important than pauses?
- How large are the heap and live set?
- Is there CPU budget for concurrent collection?
- Is memory footprint tightly constrained?

Oracle's GC tuning guidance recommends beginning with VM defaults when the application has no unusually strict pause requirements and measuring before changing collectors.

A collector is a system-level trade-off. Switching collectors can change CPU use, pause distribution, footprint, and operational behavior; it is not simply “a flag that makes Java faster.”

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-compiler-policy">Runtime-Compiler Policy</a>

<details>
<summary>Click for details</summary>

HotSpot also has runtime-compiler policy: which code is considered hot, which compilation level should be used, when code should be recompiled or deoptimized, and how compiler resources are spent.

Tiered compilation is commonly used in modern HotSpot because it balances startup, profiling quality, and peak performance.

Applications normally should not manipulate compiler thresholds as a first tuning step. These controls are implementation-sensitive and can make benchmarks behave very differently from production.

Compiler tuning only makes sense when profiling shows that compilation, warm-up, or code-cache behavior is actually relevant to the bottleneck.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="vm-options-model">The VM Options Model</a>

<details>
<summary>Click for details</summary>

VM options are startup/configuration controls exposed by a JVM implementation and launcher. Conceptually they include:

- standard Java launcher options;
- `-X...` non-standard but common controls;
- `-XX:...` advanced HotSpot-specific controls.

Not every option is permanent or stable across JDK releases.

A disciplined operational model is:

1. know which layer owns the option;
2. verify support on the exact JDK you run;
3. document the reason/SLO for each non-default flag;
4. measure before and after;
5. remove cargo-cult flags copied from older systems/JDKs.

Treat JVM configuration like operational code: version it and require evidence.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="portable-vs-implementation-specific-controls">Portable Behavior vs Implementation-Specific Controls</a>

<details>
<summary>Click for details</summary>

Portable behavior is semantics guaranteed by Java/JVM specifications. Implementation-specific controls change how one runtime achieves those semantics.

For example:

```text
object-allocation semantics
→ JVM contract

-XX:+UseG1GC
→ HotSpot control

frame semantics
→ JVM contract

code-cache sizing flag
→ HotSpot implementation control
```

Application correctness should not require an implementation-specific performance flag. VM options should influence performance/operations, not redefine program correctness.

If correctness relies on undocumented VM behavior, the design is fragile across JDK versions and vendors.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-vs-diagnostics">When to Stop Tuning and Move to Diagnostics</a>

<details>
<summary>Click for details</summary>

Tuning without evidence often becomes a loop:

```text
latency is high
→ change a GC flag
→ change heap size
→ change compiler options
→ still do not know what caused the latency
```

Move to diagnostics when the questions become:

- Is the pause actually caused by GC?
- What are the allocation rate and live-set size?
- Where are threads blocked?
- Which native-memory category is growing?
- Is code compiling or deoptimizing unexpectedly?

JVM theory produces hypotheses. Runtime Diagnostics uses JFR, `jcmd`, GC logs, dumps, NMT, and metrics to test them.

Use the loop: **measure → explain → change → measure again**.

</details>

- [Quay lại đầu trang](#back-to-top)
