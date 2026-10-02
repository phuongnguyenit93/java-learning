# 📂 README MODULE STRUCTURE (EN)

* **1.MentalModel**
    * [NativeInteroperability](readme/en/menu/1.MentalModel/NativeInteroperability.md)
* **2.NativeLibraryAbi**
    * [NativeLibraryAbi](readme/en/menu/2.NativeLibraryAbi/NativeLibraryAbi.md)
* **3.JNI**
    * [JNI](readme/en/menu/3.JNI/JNI.md)
* **4.JNIInvocation**
    * [JNIInvocation](readme/en/menu/4.JNIInvocation/JNIInvocation.md)
* **5.FFM**
    * [FFM](readme/en/menu/5.FFM/FFM.md)
* **6.MemorySegment**
    * [MemorySegment](readme/en/menu/6.MemorySegment/MemorySegment.md)
* **7.ArenaLifetime**
    * [ArenaLifetime](readme/en/menu/7.ArenaLifetime/ArenaLifetime.md)
* **8.MemoryLayout**
    * [MemoryLayout](readme/en/menu/8.MemoryLayout/MemoryLayout.md)
* **9.LinkerSymbolLookup**
    * [LinkerSymbolLookup](readme/en/menu/9.LinkerSymbolLookup/LinkerSymbolLookup.md)
* **10.DowncallUpcall**
    * [DowncallUpcall](readme/en/menu/10.DowncallUpcall/DowncallUpcall.md)
* **11.SafetyPitfalls**
    * [SafetyPitfalls](readme/en/menu/11.SafetyPitfalls/SafetyPitfalls.md)
* **12.DecisionsSynthesis**
    * [DecisionsSynthesis](readme/en/menu/12.DecisionsSynthesis/DecisionsSynthesis.md)

# Native Interoperability

Native Interoperability is the Java Advanced area focused on the boundary between the managed Java runtime and native code, native memory, and operating-system libraries. It builds the mental model for crossing that boundary through JNI and the Foreign Function & Memory API (FFM) while reasoning explicitly about ABIs, ownership, lifetime, linkage, and failure boundaries.

## Why learn this module?

Java normally hides memory and platform details to improve portability and safety. Some systems still need to call C/C++ libraries, use operating-system native APIs, access off-heap memory, or integrate with an existing binary interface. At that point, developers need more than API syntax: they need to understand which JVM guarantees no longer protect the whole execution path.

This module moves from the native boundary and binary contracts through JNI and FFM memory/function models, then closes with safety and decision-making.

## Prerequisites

You should already understand:

- Java Core classes, methods, exceptions, resource lifecycles, and basic I/O;
- the JVM at the managed-runtime, memory, and execution mental-model level;
- Dynamic Runtime concepts around MethodHandle and VarHandle before going deep into FFM linkage;
- concurrency fundamentals before sharing native state or callbacks across threads.

## Learning flow

Study the module in this order:

1. what Native Interoperability is and why Java sometimes crosses the managed boundary;
2. native libraries, symbols, ABIs, and platform compatibility;
3. the JNI bridge model and the JNI Invocation API;
4. the FFM mental model, MemorySegment, and Arena;
5. MemoryLayout for describing native data;
6. Linker, SymbolLookup, downcalls, and upcalls for foreign-function linkage;
7. ownership, safety, failures, and native-access boundaries;
8. a final JNI-versus-FFM decision model and end-to-end integration design.

## Module boundaries

Native Interoperability owns JNI, FFM, native-library loading as part of Java/native integration, native/off-heap lifetime, memory layouts, symbol/linker models, downcalls/upcalls, and safety concerns at the native boundary.

The following topics are handed off:

- JVM native-memory internals and execution internals → JVM;
- deep MethodHandle/VarHandle mechanics → Dynamic Runtime;
- release-by-release FFM preview/finalization history → Java Version, especially Java 22 FFM;
- full C/C++ language, compiler-toolchain, and platform-specific ABI engineering → outside the Java curriculum;
- generic OS/process administration → the corresponding infrastructure/runtime owner.

The end goal is to trace a native integration from library and ABI through memory, calls/callbacks, error propagation, and cleanup, while knowing when JNI, FFM, or a managed-Java solution is the better fit.
