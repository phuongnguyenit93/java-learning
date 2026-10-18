<a id="back-to-top"></a>

# Native Interoperability and the Java-to-Native Boundary

## Menu
- [What Is Native Interoperability?](#native-interoperability-definition)
- [Why Does Java Need Native Interoperability?](#native-interoperability-purpose)
- [Managed Runtime and the Native Boundary](#managed-native-boundary)
- [When Does Java Need to Go Native?](#native-interoperability-use-cases)
- [Costs and Risks of Crossing the Native Boundary](#native-boundary-costs)

## <a id="native-interoperability-definition">What Is Native Interoperability?</a>

<details>
<summary>Click for details</summary>

Native interoperability is the set of mechanisms that let Java code cross from the managed Java runtime into code, data, or services represented in a native form. Typical targets are operating-system libraries, existing C APIs, device/vendor libraries, and memory whose lifetime is not managed as ordinary Java objects.

The key mental model is a **boundary between two execution worlds**. On the Java side, the JVM gives you managed objects, garbage collection, Java type safety, Java exceptions, and Java thread/runtime rules. On the native side, the contract is expressed through binary symbols, addresses, calling conventions, native data layouts, explicit resource lifetime, and platform-specific behavior.

This module studies the Java-facing mechanisms used to cross that boundary:

```text
Java code
   |
   +--> JNI  ------------------> native implementation using the JNI contract
   |
   +--> FFM  ------------------> native memory + foreign functions
                                   through MemorySegment, Arena,
                                   MemoryLayout, Linker, SymbolLookup
```

The goal is not to learn C or operating-system internals in full. The goal is to understand what Java must describe, own, validate, and clean up when the JVM no longer controls the whole operation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-interoperability-purpose">Why Does Java Need Native Interoperability?</a>

<details>
<summary>Click for details</summary>

Pure Java is usually the simplest and most portable option, but some useful capabilities already exist outside the Java platform. Reimplementing them can be expensive, impossible, or undesirable.

Native interoperability matters when Java must reuse an existing native investment or reach a capability exposed through a native ABI. Examples include mature C libraries, system APIs, codecs, database engines, scientific libraries, hardware SDKs, or memory shared with another runtime.

Without an interoperability mechanism, Java code would have no standard way to answer questions such as:

- which native library should be loaded?
- which symbol represents the function we want?
- what binary signature does that function expect?
- how is a native structure laid out in memory?
- who owns allocated memory and when is it released?
- how does native code call back into Java?

JNI and FFM solve these questions at different abstraction levels. JNI is the long-standing JVM/native interface centered on native methods and the JNI function table. FFM gives Java APIs for foreign functions and foreign memory, with explicit models for segments, layouts, arenas, linkers, and symbols.

Native interoperability therefore exists because the Java ecosystem sometimes has to participate in contracts that are defined outside the Java object model.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="managed-native-boundary">Managed Runtime and the Native Boundary</a>

<details>
<summary>Click for details</summary>

Inside managed Java, many safety and lifecycle rules are enforced by the language and JVM. An ordinary object reference does not expose a raw address, and the garbage collector can reclaim an object after it becomes unreachable. Array access is bounds-checked, Java method signatures are type-checked, and Java exceptions have defined propagation semantics.

Crossing into native code changes the assumptions. A native function may expect a pointer to exactly 24 bytes with a particular alignment. A shared library may expose a symbol whose binary signature must match the caller. Native code can dereference invalid addresses, write outside an allocation, retain a callback after Java has released its supporting resources, or crash the process.

Think of the boundary as a contract translation:

| Managed Java concept | Native-boundary counterpart |
| --- | --- |
| Java type | ABI carrier / native layout |
| object reference | JNI reference or native address |
| GC-managed lifetime | explicit/native resource lifetime |
| Java method call | native symbol + calling convention |
| Java exception | JNI pending exception, error code, errno-style contract, or process failure |

The JVM still runs the Java side, but it cannot make arbitrary native code memory-safe. Interoperability APIs reduce classes of mistakes and make responsibilities explicit; they do not turn native execution into ordinary managed Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-interoperability-use-cases">When Does Java Need to Go Native?</a>

<details>
<summary>Click for details</summary>

Going native is justified when the capability you need is naturally owned by a native interface and the value outweighs the added deployment and safety cost.

Common cases include:

- calling an established native library instead of rewriting it in Java;
- integrating with an operating-system or hardware API that has no suitable Java wrapper;
- exchanging data with another runtime through native memory;
- embedding Java in a native host application through the JNI Invocation API;
- exposing a Java callback to a native library;
- working with file mappings or other native-memory-oriented data flows through FFM.

A useful decision question is: **what specific boundary forces this integration to exist?** If the answer is merely “native might be faster,” the design is incomplete. The actual cost can include copying, transition overhead, deployment complexity, platform variants, and harder failure analysis.

Start from the external contract. Identify the library, ABI, data representation, ownership model, and error model first. Only then choose JNI, FFM, or an existing maintained wrapper.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-boundary-costs">Costs and Risks of Crossing the Native Boundary</a>

<details>
<summary>Click for details</summary>

Crossing the native boundary removes several assumptions that Java developers normally rely on. The most serious costs are not syntax costs; they are **contract, lifetime, portability, and failure costs**.

Typical risks include:

- loading the wrong library build for the current OS or CPU;
- describing a native function with the wrong ABI signature;
- using the wrong size, alignment, or field offset for native data;
- retaining JNI local references beyond their valid lifetime;
- leaking global references, native allocations, file mappings, or callback stubs;
- using an FFM segment after its scope has become invalid;
- sharing confined resources across the wrong thread;
- allowing native code to write outside valid memory;
- receiving a fatal native fault that terminates the JVM process instead of producing a normal Java exception.

The practical response is to make every boundary explicit:

```text
library + platform
        ↓
symbol + ABI
        ↓
data layout + memory ownership
        ↓
call/callback lifetime
        ↓
error propagation + cleanup
```

These concerns drive the rest of the module. JNI and FFM differ in API shape, but both ultimately depend on correct binary contracts and disciplined resource ownership.

</details>

- [Quay lại đầu trang](#back-to-top)
