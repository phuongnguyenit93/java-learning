<a id="back-to-top"></a>

# End-to-End JVM Mental Model

## Menu
- [From JVM Startup to Method Execution](#startup-to-execution-flow)
- [From Class Files to Runtime State](#classfile-to-runtime-state-flow)
- [Method Execution Through Frames, the Operand Stack, and the Execution Engine](#method-execution-flow)
- [From Object Allocation to Memory Reclamation](#allocation-to-reclamation-flow)
- [From Interpreted Execution to Optimized Code](#interpreted-to-optimized-flow)
- [Viewing the JVM as a Whole Process](#whole-process-memory-view)
- [Mapping Problems to the Correct Module Owner](#jvm-problem-boundary-map)
- [JVM Mental-Model Checkpoint](#jvm-mental-model-checkpoint)

## <a id="startup-to-execution-flow">From JVM Startup to Method Execution</a>

<details>
<summary>Click for details</summary>

When a JVM process starts, the runtime establishes VM-wide structures, threads, and the runtime policies/ergonomic defaults it needs. The initial application class is then loaded, linked, and initialized according to lifecycle rules before execution proceeds through the entry method.

```text
process startup
→ JVM runtime initialization
→ initial class loading/linking/initialization
→ entry-method frame
→ bytecode execution
→ adaptive optimization over time
```

This does not mean every application class is eagerly loaded at startup. Loading and linking continue dynamically as execution discovers additional types.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classfile-to-runtime-state-flow">From Class Files to Runtime State</a>

<details>
<summary>Click for details</summary>

A class file begins as a binary representation containing version information, the constant pool, fields, methods, and attributes.

When the runtime needs the type:

```text
class-file representation
→ load/create runtime class
→ verify + prepare (+ resolve as needed)
→ runtime constant pool / class metadata
→ initialize static state when triggered
```

Instruction execution can then operate against runtime entities rather than only symbolic entries in a file.

This is the bridge between the class-file chapter and runtime-data chapters: data “in the binary” becomes runtime structures used by execution.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-execution-flow">Method Execution Through Frames, the Operand Stack, and the Execution Engine</a>

<details>
<summary>Click for details</summary>

When a method is invoked, the executing thread receives a new frame containing local variables, an operand stack, and a runtime-constant-pool reference.

```text
invoke
→ create/push frame
→ bytecode consumes locals/operand stack
→ nested invoke creates another frame
→ return or exception
→ frame is destroyed
```

The execution engine may interpret the method's bytecode or run a compiled native version. Even though those implementation paths differ, observable Java semantics must still satisfy the contract.

The stack-frame model is therefore **logical execution state**; the final CPU registers and machine-stack layout remain implementation details.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="allocation-to-reclamation-flow">From Object Allocation to Memory Reclamation</a>

<details>
<summary>Click for details</summary>

An object begins with an allocation request and becomes part of the application's object graph.

```text
new
→ object storage
→ constructor <init> initialization
→ references keep the object reachable
→ application changes the reference graph
→ object becomes unreachable
→ GC may reclaim its storage
```

“May reclaim” does not mean immediate reclamation. Timing depends on collector behavior and runtime state.

If application code accidentally retains a reference, the object remains live from the collector's point of view even when the business logic no longer needs it. That is a common form of managed-memory leak.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpreted-to-optimized-flow">From Interpreted Execution to Optimized Code</a>

<details>
<summary>Click for details</summary>

In HotSpot, a method can begin interpreted, gather profile data, and later be JIT compiled as it becomes hot.

```text
cold bytecode
→ interpreted
→ profiled
→ compiled
→ optimized
→ assumption invalid?
   └─→ deopt / recompile
```

This is a dynamic lifecycle rather than a single permanent transition.

One consequence is that JVM benchmarking needs warm-up and disciplined measurement. Startup/cold-path latency can differ significantly from steady-state behavior.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="whole-process-memory-view">Viewing the JVM as a Whole Process</a>

<details>
<summary>Click for details</summary>

A production JVM process is the sum of multiple memory/resource categories:

```text
managed heap
+ thread/native stacks
+ class metadata
+ compiled code/code cache
+ direct/off-heap buffers
+ native libraries/runtime internals
= process footprint
```

Heap tuning affects only one part of that total. Thread explosion, a class-loader leak, or native-buffer growth can create memory pressure independently of heap occupancy.

The whole-process view prevents the common mistake “heap graphs look fine, therefore there cannot be a memory problem.”

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-problem-boundary-map">Mapping Problems to the Correct Module Owner</a>

<details>
<summary>Click for details</summary>

When a runtime problem appears, first identify the owning domain:

```text
Where did a class come from? delegation/loader identity?
→ Class Loading

MethodHandle / CallSite / VarHandle / linkage API?
→ Dynamic Runtime

agent / transform / redefine class?
→ Instrumentation

JNI / FFM / native call / explicit native-memory lifetime?
→ Native Interoperability

thread safety / happens-before?
→ Concurrency

heap dump / JFR / NMT / root-cause evidence?
→ Runtime Diagnostics
```

The JVM module is the foundation map: it tells you which runtime mechanism is involved so you can move to the correct specialized module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-mental-model-checkpoint">JVM Mental-Model Checkpoint</a>

<details>
<summary>Click for details</summary>

After this module, you should be able to explain these questions without relying on a memorized list of VM flags:

1. What execution contract does the JVM consume, and what role does the class file play?
2. How are loading/linking/initialization different from method execution?
3. How do the heap, method area, JVM stack, frames, and runtime constant pool relate?
4. Why does a source-level `new` not guarantee a physical heap object that survives until GC?
5. Why is there no single mandatory “Java GC algorithm”?
6. How do interpretation, profiling, JIT compilation, and deoptimization form adaptive execution?
7. Why can RSS grow while heap usage stays stable?
8. Which VM controls are HotSpot details rather than Java/JVM contract?
9. When should reasoning stop and diagnostics begin?

If you can answer those as a connected flow rather than isolated definitions, your JVM mental model is ready for the later Java Advanced modules.

</details>

- [Quay lại đầu trang](#back-to-top)
