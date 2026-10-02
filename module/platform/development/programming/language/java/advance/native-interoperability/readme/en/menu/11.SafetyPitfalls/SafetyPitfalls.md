<a id="back-to-top"></a>

# Ownership, Safety, and Failure Boundaries

## Menu
- [Ownership and Cleanup of Native Resources](#native-resource-ownership)
- [Native Library Loading and Linkage Failures](#native-loading-failures)
- [JNI Reference Lifetime Failures](#jni-reference-failures)
- [Preview Enablement, Restricted Operations, and Native Access](#native-access-control)
- [Layout, Descriptor, and ABI Mismatches](#layout-descriptor-mismatch)
- [Use-After-Close and Temporal-Safety Failures](#lifetime-failures)
- [Java Exceptions, Native Error Codes, and Error Propagation](#native-error-boundary)
- [Thread Safety and Shared Native State](#native-thread-safety)
- [JVM Crashes and Silent Memory Corruption](#native-crash-corruption)

## <a id="native-resource-ownership">Ownership and Cleanup of Native Resources</a>

<details>
<summary>Click for details</summary>

At the native boundary, every resource needs a clear **owner** and a clear release point. A resource is not only native memory; it can also be a JNI global reference, an upcall stub, a native-library association, a file mapping, or a handle returned by a C library.

A small ownership table often exposes lifecycle mistakes early:

| Resource | Created by | Released by | Lifetime |
| --- | --- | --- | --- |
| Allocation from a closeable Arena (`ofConfined` / `ofShared`) | Java binding | Arena.close | task/component scoped |
| Allocation from `Arena.ofAuto()` / `Arena.global()` | Java binding/runtime | not manually closed | GC-managed lifetime / process lifetime |
| JNI global reference | native code | DeleteGlobalRef | until the Java object no longer needs to be retained |
| upcall stub | Java binding | follows its associated Arena; `Arena.close()` only when that Arena is closeable | until the callback is unregistered and the Arena lifetime ends |
| native handle | native library | matching close/free API | defined by the library contract |

Not every Arena is closeable: `ofConfined` and `ofShared` support explicit close, while `ofAuto` and `global` are not released through `Arena.close()`. If the owner, release action, or lifetime cannot be stated precisely, the integration is not yet safe enough to rely on.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-loading-failures">Native Library Loading and Linkage Failures</a>

<details>
<summary>Click for details</summary>

Native-library problems can fail at several distinct layers. Separating them makes diagnosis much easier.

~~~text
file cannot be found
→ loading failure

file exists but architecture/dependency is wrong
→ loader/linker failure

library loads but expected symbol is missing
→ symbol-resolution failure

symbol exists but descriptor/ABI is wrong
→ call-boundary failure, potentially a crash
~~~

JNI commonly surfaces loading or binding problems through UnsatisfiedLinkError. With FFM, SymbolLookup.find may simply fail to produce the expected symbol. The operating-system loader can also report missing native dependencies or an invalid binary format.

When debugging, record the library path/name, OS and CPU architecture, native dependencies, and expected symbol/version instead of collapsing everything into “native call failed.”

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-reference-failures">JNI Reference Lifetime Failures</a>

<details>
<summary>Click for details</summary>

JNI reference-lifetime bugs usually fall into two categories: **using a reference after its valid lifetime** and **keeping a reference alive longer than intended**.

A local reference is always tied to its JNI thread and local frame. A local created during a Java → native method call becomes invalid when that native method returns, so storing it in native global state for a later call is incorrect. An Invocation-attached native thread is different: its locals can survive across multiple JNI calls until explicit deletion/frame cleanup or detachment, but they still are not cross-thread references. If the native side must retain a Java object independently of that local frame/thread lifetime, it needs a global reference.

The opposite problem is a leaked global reference. A global reference keeps the Java object reachable until DeleteGlobalRef is called, so forgetting to release it can prevent garbage collection indefinitely.

Long native loops can also accumulate too many local references. DeleteLocalRef or local frames keep that working set bounded.

The practical rule is simple: the JNI reference lifetime should match the lifetime of the Java object relationship it represents—no shorter and no longer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-access-control">Preview Enablement, Restricted Operations, and Native Access</a>

<details>
<summary>Click for details</summary>

Java 21 has **two separate gates** that are easy to confuse.

First, FFM is a preview API, so compilation and execution need preview features enabled:

~~~bash
javac --release 21 --enable-preview NativeDemo.java
java --enable-preview NativeDemo
~~~

Second, some FFM methods are **restricted operations** because they can execute native code or weaken safety assumptions. In Java 21, if `--enable-native-access` is omitted entirely, those calls are still allowed but the runtime emits warnings. Once the option is present, only the listed modules are granted native access; a restricted call from a caller outside that set is rejected with `IllegalCallerException`. Native access should therefore be granted to the specific modules that actually perform restricted operations.

For code on the class path:

~~~bash
java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     NativeDemo
~~~

Do not interpret --enable-native-access as “turning on FFM.” Preview enablement and native-access declaration are separate concerns. For named modules, grant native access only to the modules that actually need restricted operations.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layout-descriptor-mismatch">Layout, Descriptor, and ABI Mismatches</a>

<details>
<summary>Click for details</summary>

Three contracts must agree: the **MemoryLayout of the data**, the **FunctionDescriptor of the call**, and the **ABI of the native binary**.

Dangerous mismatches include:

- a struct layout missing required padding;
- a pointer parameter described as an integer;
- a function returning a struct while the descriptor models a scalar;
- a binary using a different calling convention;
- code generated from header version A while runtime loads library version B.

Some mismatches are caught by Java or Linker validation, but not all of them. A native function pointer does not carry enough type information for the runtime to prove that the descriptor is correct.

Bindings should therefore include boundary tests with known values: verify structure size and offsets, write data on one side and read it on the other, and exercise the exact library version that will be deployed.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifetime-failures">Use-After-Close and Temporal-Safety Failures</a>

<details>
<summary>Click for details</summary>

Use-after-close happens when code keeps a MemorySegment or callback address beyond the lifetime of the resource that backs it.

FFM reduces this risk through temporal safety. A segment associated with a closeable Arena becomes invalid after that Arena is closed:

~~~java
Arena arena = Arena.ofConfined();
MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
arena.close();

segment.get(ValueLayout.JAVA_LONG, 0); // lifetime has ended
~~~

That Java-side check cannot protect native code that previously stored the raw address. If a native library keeps a pointer and later dereferences it after the Arena has closed, the fault is outside the segment's temporal-safety boundary.

Any API that “registers a pointer or callback for later” therefore needs a matching lifecycle protocol: keep the Arena alive, unregister successfully, and only then close the Arena.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-error-boundary">Java Exceptions, Native Error Codes, and Error Propagation</a>

<details>
<summary>Click for details</summary>

Java and native libraries often use different error models. Java uses exceptions; a C API may return a negative status, NULL, errno, or a library-specific error object.

The binding layer should translate that boundary deliberately:

~~~text
native return/status
        ↓ inspect result according to the library contract
Java exception/result with context
        ↓
application handles the Java contract
~~~

With JNI, a Java exception raised during a native call becomes a pending exception and must be propagated, inspected, or cleared intentionally. With an FFM downcall, a native error code does not automatically become a Java exception; the binding must inspect the result according to the library contract.

Call state such as `errno` needs a stricter treatment. In Java 21, where the current platform exposes that state through the linker, use `Linker.Option.captureCallState("errno")` (or another supported captured-state name). The downcall then captures the state immediately after the foreign call into a caller-provided segment, before later Java/runtime activity can overwrite the conventional thread-local native error state. Supported captured-state names and their layout are platform-dependent and can be inspected through `Linker.Option.captureStateLayout()`.

Avoid exposing two competing error channels for the same operation unless callers have a clear rule for which one is authoritative.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-thread-safety">Thread Safety and Shared Native State</a>

<details>
<summary>Click for details</summary>

Making the same native memory visible to multiple threads does not make the data thread-safe. A shared Arena permits access from multiple threads, but it does not add locks or an atomic protocol around the native structure.

Common race sources include:

- Java and native threads writing the same buffer;
- a callback arriving while Java is closing a resource;
- a native library with global state that requires serialized calls;
- a JNI global reference pointing to mutable Java state that is not thread-safe.

Before sharing state, define who may read and write it, when closing is allowed, and whether the native library itself documents thread-safety guarantees.

If the data is truly single-threaded, confined ownership is usually easier to reason about than shared native state.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-crash-corruption">JVM Crashes and Silent Memory Corruption</a>

<details>
<summary>Click for details</summary>

The most important difference between ordinary Java failures and native failures is **failure severity**. A NullPointerException normally leaves the JVM process alive; native memory corruption can damage or terminate the entire process before Java can throw anything useful.

Typical causes include:

- invoking a function pointer with the wrong descriptor;
- native code writing past a buffer boundary;
- dereferencing a pointer after its lifetime has ended;
- calling an upcall stub after it has been released;
- ABI or layout mismatches;
- bugs inside the native library itself.

Silent memory corruption is often worse than an immediate crash because the visible symptom can appear long after the original mistake.

Prevention matters more than exception handling: keep the native boundary small, use FFM spatial and temporal checks, validate descriptors and layouts, make ownership explicit, and use native sanitizers or debuggers when appropriate. No Java try/catch can turn arbitrary undefined native behavior into a recoverable Java error.

Once those failure boundaries are understood, the final chapter turns them into design criteria: when to choose JNI, FFM, an existing wrapper, or no native boundary at all, and how to review the integration end to end.

</details>

- [Quay lại đầu trang](#back-to-top)
