<a id="back-to-top"></a>

# JVM Purpose and Execution Contract

## Menu
- [What Is the JVM and Why Does It Exist?](#jvm-purpose)
- [The Class File as the JVM Execution Contract](#classfile-execution-contract)
- [JVM Language Independence](#jvm-language-independence)
- [Portability Through the Virtual-Machine Layer](#jvm-portability-model)
- [JVM Specification vs JVM Implementation](#jvm-specification-vs-implementation)
- [The JVM Runtime Responsibility Boundary](#jvm-runtime-boundary)

## <a id="jvm-purpose">What Is the JVM and Why Does It Exist?</a>

<details>
<summary>Click for details</summary>

The JVM is an abstract machine that executes programs represented in the **class-file format** instead of executing Java source code directly on a CPU. This extra execution layer lets compilers target a stable runtime contract while each JVM implementation maps that contract onto a concrete operating system, processor architecture, memory subsystem, and optimization strategy.

The problem the JVM solves is broader than “run Java on several operating systems.” Without a shared execution contract, each language/compiler toolchain would need far tighter coupling to every target machine and would have much less opportunity to share libraries, runtime services, garbage collectors, JIT compilers, and tooling.

A simpler alternative is compiling source directly to native machine code for each target. That can execute the program, but portability and distribution then move toward target-specific compiler outputs, and there is no shared **JVM contract** that every target is required to implement. A different native runtime can still provide its own garbage collector or tooling; it is simply a different runtime contract. The JVM inserts a common class-file execution layer between program representation and the host machine.

Keep this model throughout the module:

```text
source language
    ↓ compiler
class-file representation
    ↓ JVM implementation
runtime state + native machine execution
```

The JVM therefore exists both as a specification-level abstraction and as concrete implementations such as HotSpot. HotSpot is a JVM implementation; it is not the definition of “the JVM.”

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classfile-execution-contract">The Class File as the JVM Execution Contract</a>

<details>
<summary>Click for details</summary>

The JVM Specification defines the **class-file format** as the binary contract a compliant JVM implementation understands. A class file carries version information, a constant pool, access flags, fields, methods, and attributes. Executable methods normally carry a `Code` attribute containing bytecode plus execution metadata.

The runtime does not need the original source language. If a toolchain produces a valid class-file representation, the JVM can process it according to the same contract.

The class file also does not prescribe the physical address of an object, the exact object-header layout, or a garbage-collection algorithm. It defines logical program structure and symbolic references; a runtime implementation decides how those concepts are represented and optimized on a real machine.

When reasoning about JVM behavior, first ask:

```text
Is this guaranteed by the class-file/JVM contract?
Or is it behavior of one concrete JVM implementation?
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-language-independence">JVM Language Independence</a>

<details>
<summary>Click for details</summary>

The JVM is not a machine that understands only Java source code. It understands the structures expressible through the class-file format and JVM instruction set. The Java compiler is one class-file producer; other JVM languages can target the same runtime.

That common substrate makes it possible for languages to:

- reuse Java libraries;
- interoperate when their JVM-level types and conventions are compatible;
- share the same garbage collector, JIT compiler, profiler, and diagnostic tooling;
- run inside the same process/runtime.

Sharing a JVM does **not** mean every language feature maps one-to-one to a Java feature. A compiler may encode its semantics using synthetic methods, `invokedynamic`, metadata, or a language-specific runtime library.

This module focuses on the common execution substrate, not the language semantics of every JVM language.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-portability-model">Portability Through the Virtual-Machine Layer</a>

<details>
<summary>Click for details</summary>

Java portability comes from distributing a platform-neutral class-file representation rather than machine code compiled for every CPU/OS combination. The JVM installed on each target platform implements the common execution contract.

```text
same class file
   ├─→ JVM on Windows/x64
   ├─→ JVM on Linux/x64
   └─→ JVM on Linux/ARM64
```

This reduces coupling between the application artifact and a machine instruction set.

Portability is still not absolute. Application behavior can depend on file-system semantics, native libraries, default charset, OS scheduling, environment limits, or implementation-specific VM options.

So “write once, run anywhere” is best understood as a **portable execution contract**, not a guarantee that every environmental detail is identical.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-specification-vs-implementation">JVM Specification vs JVM Implementation</a>

<details>
<summary>Click for details</summary>

The JVM Specification defines the contract a JVM must preserve: the class-file format, logical runtime data areas, instruction semantics, class lifecycle rules, frames, exception behavior, and other runtime rules.

A concrete implementation such as HotSpot must preserve that contract while remaining free to choose many internal details:

- physical object layout;
- garbage collector;
- JIT strategy;
- internal memory organization;
- code-cache organization;
- thresholds and heuristics;
- implementation-specific VM flags.

For example, the specification defines a heap used for objects and arrays and allows automatic storage management. It does **not** require a young/old-generation layout or require G1.

Likewise, the JVM is not inherently interpreted. A compliant implementation can interpret instructions, compile them to native code, or combine several execution strategies as long as the required semantics are preserved.

A useful rule is:

> Specification guarantees are the portable contract; HotSpot documentation describes implementation behavior.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-runtime-boundary">The JVM Runtime Responsibility Boundary</a>

<details>
<summary>Click for details</summary>

This JVM module owns the mental model of how class-file code becomes runtime state and executes: runtime data areas, frames, the managed heap, garbage-collection concepts, interpretation/JIT compilation, native-memory awareness, and VM ergonomics.

Several neighboring topics touch the JVM but have different owners:

- **Class Loading** owns ClassLoader APIs, delegation, and class-loader identity in depth.
- **Concurrency** owns happens-before, the Java Memory Model, and correctness between threads.
- **Dynamic Runtime** owns the MethodHandle/CallSite/VarHandle programming model.
- **Instrumentation** owns Java agents and class transformation.
- **Native Interoperability** owns JNI, FFM, and native-call/memory programming.
- **Runtime Diagnostics** owns JFR, NMT, dumps, logs, and evidence-driven troubleshooting.

Keeping these boundaries clear lets this module explain the **runtime mechanism** while downstream modules explain how to extend, control, or diagnose it.

The module follows one continuous learning path:

```text
JVM + class-file contract
→ class files / bytecode / pre-execution lifecycle
→ runtime data areas + frames
→ managed heap + garbage collection
→ interpreter + JIT + adaptive optimization
→ whole-process memory
→ HotSpot ergonomics / VM controls
→ end-to-end JVM mental model
```

Each later chapter expands one part of that execution model rather than introducing an unrelated catalog of runtime terms.

</details>

- [Quay lại đầu trang](#back-to-top)
