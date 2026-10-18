<a id="back-to-top"></a>

# Choosing JNI or FFM and Designing End to End

## Menu
- [How Do JNI and FFM Differ?](#jni-vs-ffm)
- [Criteria for Choosing an Interoperability Mechanism](#interop-selection-criteria)
- [Deployment, Portability, and Version Constraints](#deployment-portability)
- [Designing a Native Integration End to End](#end-to-end-native-integration)
- [When Should Native Interoperability Not Be Used?](#when-not-to-use-native)
- [Boundaries with JVM, Dynamic Runtime, and Java Version](#native-interoperability-handoffs)

## <a id="jni-vs-ffm">How Do JNI and FFM Differ?</a>

<details>
<summary>Click for details</summary>

JNI and FFM both solve Java-to-native interoperability, but they optimize for different integration models.

| Aspect | JNI | FFM |
| --- | --- | --- |
| Primary model | native methods + JNI function interface | foreign memory + foreign functions |
| Glue code | commonly requires C/C++ JNI glue | many bindings can be written directly in Java |
| Java object interaction | deep access through JNIEnv | not the primary model |
| C-style function APIs | possible, often with more boilerplate | maps naturally to SymbolLookup and Linker |
| Memory/resource lifetime | JNI has Java-object/reference semantics, but native allocations/resources need separate management and there is no Arena-like scoped-memory model | native-memory lifetime is modeled explicitly with MemorySegment and Arena |
| Java 21 baseline | long-established API | preview API under JEP 442 |

Neither mechanism is universally better. A stable existing JNI wrapper does not need to be rewritten simply because FFM is newer. Conversely, a new C API built around functions, pointers, and explicit layouts can often use FFM with much less handwritten native glue.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interop-selection-criteria">Criteria for Choosing an Interoperability Mechanism</a>

<details>
<summary>Click for details</summary>

Choose the mechanism by starting from the real native contract, not from API preference.

Useful questions include:

1. Is the native surface a straightforward C function API, or does it interact deeply with Java objects and classes?
2. Is there already a tested and maintained JNI wrapper?
3. Can the application's JDK baseline accept Java 21 preview FFM?
4. How complicated are callbacks, pointer ownership, and resource lifetimes?
5. Which OS and CPU architectures must be supported?
6. Can the team diagnose native crashes, ABI mismatches, and packaging failures?

A good binding should also expose a small Java-facing API so that ordinary callers do not need to know about JNIEnv, raw symbol names, or native lifetime details.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="deployment-portability">Deployment, Portability, and Version Constraints</a>

<details>
<summary>Click for details</summary>

Native interoperability makes deployment part of correctness.

An application may need to manage:

- a native artifact for each supported OS and CPU architecture;
- transitive native dependencies;
- search and load paths;
- symbol and library-version compatibility;
- JDK/runtime options such as --enable-preview and native-access declarations for Java 21 FFM.

Portability is no longer just “ship one JAR everywhere.” The Java layer can remain portable while the native binary must be built and packaged separately for each target.

Version constraints should also be separated: JDK version, JNI/FFM API version, native header version, and binary library version are different contracts. The test matrix should cover the combinations that are actually supported rather than only the developer's machine.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="end-to-end-native-integration">Designing a Native Integration End to End</a>

<details>
<summary>Click for details</summary>

An end-to-end native integration is easier to reason about as a chain of contracts instead of a single API call.

~~~text
1. Identify the native capability that is required
        ↓
2. Choose JNI or FFM
        ↓
3. Fix the library, platform, and ABI target
        ↓
4. Define data layouts and function signatures
        ↓
5. Define ownership and lifetime
        ↓
6. Define call/callback and thread boundaries
        ↓
7. Translate errors across the boundary
        ↓
8. Package, test, and release resources
~~~

Each step should have observable evidence. For example: confirm the symbol exists, verify layout sizes and offsets, test callback registration/unregistration repeatedly, test shutdown behavior, and run on every supported architecture.

When an integration is difficult to debug, walking this chain helps identify whether the problem belongs to loading, ABI, data layout, lifetime, threading, or error propagation instead of treating all native behavior as one opaque box.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="when-not-to-use-native">When Should Native Interoperability Not Be Used?</a>

<details>
<summary>Click for details</summary>

Do not use native interoperability merely because an API looks “lower level” or because of an unmeasured assumption that native code will be faster.

Prefer managed Java when:

- the JDK or a Java library already provides the required capability;
- the native benefit is small but the deployment matrix is large;
- the team cannot control or validate the native binary versions;
- failure isolation matters more than a small performance gain;
- the workload is dominated by I/O or networking rather than the native computation itself.

Crossing the boundary also has overhead and makes profiling and debugging more difficult. Accept that cost only when the native side provides a capability or measurable benefit that justifies it.

“Do not use native interoperability here” is a valid engineering conclusion.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-interoperability-handoffs">Boundaries with JVM, Dynamic Runtime, and Java Version</a>

<details>
<summary>Click for details</summary>

This module stops at the **Java-to-native boundary** and deliberately hands deeper topics to the modules that own them.

- **JVM** owns runtime internals, JVM native-memory internals, and the execution engine.
- **Dynamic Runtime** owns deep MethodHandle/VarHandle mechanics, adaptation, and typed invocation.
- **Java Version / Java 22 FFM** owns release-by-release FFM evolution and the finalization milestone.
- **Outside the Java curriculum** are the C/C++ language, compiler/linker toolchains, and platform-specific ABI engineering in depth.
- **Infrastructure/runtime** owns general OS and process administration.

Here, the learner only needs enough of those neighboring concepts to design a correct native integration. For example, Linker returns MethodHandle, but this module does not reteach the full MethodHandle combinator model; Java 22 finalizes FFM, but the working baseline here remains Java 21 preview.

The final checkpoint is being able to explain the complete path: choose and load a native library, define the data and function contracts, manage memory and callback lifetimes, invoke through JNI or FFM, and propagate errors while releasing resources safely.

</details>

- [Quay lại đầu trang](#back-to-top)
