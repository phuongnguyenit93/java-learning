<a id="back-to-top"></a>

# Foreign Function & Memory API

## Menu
- [What Is FFM and Why Does It Exist?](#ffm-definition-purpose)
- [How Do FFM and JNI Divide the Problem Space?](#ffm-jni-relationship)
- [Java 21 Preview and Java 22 Finalization](#ffm-java21-java22-boundary)
- [MemorySegment, Arena, MemoryLayout, Linker, and SymbolLookup](#ffm-core-abstractions)
- [The FFM Safety Model](#ffm-safety-model)
- [Restricted Operations and Native Access](#ffm-restricted-operations)

## <a id="ffm-definition-purpose">What Is FFM and Why Does It Exist?</a>

<details>
<summary>Click for details</summary>

The Foreign Function & Memory API (FFM) is Java's API for interacting with memory and functions outside the Java runtime. It brings two related problems under one model:

- **foreign memory**: representing and accessing memory regions that may live outside the Java heap;
- **foreign functions**: locating native symbols and calling them through the platform ABI.

The API exists because native integration needs more than a raw address. Java must also know how large a region is, how long it remains valid, which thread may access it, how native data is laid out, and what binary signature a function expects.

FFM makes those concerns explicit through Java objects instead of forcing the application to encode all boundary logic in hand-written JNI glue.

```text
native memory         → MemorySegment + Arena + MemoryLayout
native function       → SymbolLookup + FunctionDescriptor + Linker
Java ↔ native calls   → downcall / upcall MethodHandle boundary
```

The API improves the Java-side model of interoperability, but it does not make native code safe. A wrong native descriptor, unsafe pointer reinterpretation, or buggy foreign function can still corrupt memory or crash the process.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ffm-jni-relationship">How Do FFM and JNI Divide the Problem Space?</a>

<details>
<summary>Click for details</summary>

JNI and FFM both cross the Java/native boundary, but they expose different programming models.

JNI centers the bridge on the JVM's native interface. Java declares `native` methods, while native code receives `JNIEnv*` and uses JNI operations to work with Java objects, methods, fields, references, threads, and exceptions.

FFM centers the bridge on **native contracts visible from Java**. Java code can describe memory layouts, allocate native memory, locate exported symbols, describe function signatures, and obtain call handles without writing a JNI wrapper for every ordinary C function.

| Need | JNI | FFM |
| --- | --- | --- |
| existing JNI library / native method contract | natural fit | usually not a direct replacement |
| manipulate Java objects deeply from native code | JNI function interface | not FFM's main model |
| call C-style exported functions from Java | usually JNI wrapper code | direct Linker/SymbolLookup model |
| native memory/data layout | manual native/JNI discipline | first-class MemorySegment/Layout/Arena model |
| embed a JVM in a native host | JNI Invocation API | outside FFM's purpose |

Do not reduce the choice to “old versus new.” Existing API shape, callback needs, Java-object interaction, deployment constraints, and JDK baseline all matter.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ffm-java21-java22-boundary">Java 21 Preview and Java 22 Finalization</a>

<details>
<summary>Click for details</summary>

In this module the baseline is **Java 21**, where FFM is a preview API delivered by JEP 442. Code that uses `java.lang.foreign` preview APIs must be compiled and run with preview features enabled for Java 21.

For example:

```text
javac --release 21 --enable-preview Example.java
java  --enable-preview Example
```

FFM was finalized in Java 22. That release-history milestone belongs to the Java Version curriculum; this module keeps its examples and API assumptions aligned with Java 21 unless a later-version difference is explicitly identified.

Two different controls must not be confused:

```text
--enable-preview
→ allows use of the Java 21 preview API surface

--enable-native-access=...
→ grants native access to the selected modules for restricted operations
```

Preview enablement and native-access control are independent. In Java 21, preview is required to compile/run this FFM API. Native access governs authorization for restricted operations: omitting the option allows calls with warnings, while specifying it grants access only to the listed modules.

When reading later JDK documentation, check signatures carefully because FFM evolved before finalization. Code written for Java 22+ is not automatically valid Java 21 source.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ffm-core-abstractions">MemorySegment, Arena, MemoryLayout, Linker, and SymbolLookup</a>

<details>
<summary>Click for details</summary>

FFM is easier to understand as a small set of cooperating abstractions rather than a large bag of methods.

| Abstraction | Responsibility |
| --- | --- |
| `MemorySegment` | bounded view of a contiguous memory region |
| `Arena` | allocation plus lifetime/thread-access policy for native segments |
| `MemoryLayout` | size, alignment, and structured interpretation of bytes |
| `SymbolLookup` | find a native symbol and obtain its address |
| `Linker` | adapt Java calls/callbacks to the current native ABI |
| `FunctionDescriptor` | describe native function parameter/return layouts |

They connect in an end-to-end foreign-call flow:

```text
Arena allocates arguments
      ↓
MemorySegment holds native data
      ↓
MemoryLayout describes its shape
      ↓
SymbolLookup finds function
      ↓
FunctionDescriptor describes signature
      ↓
Linker creates downcall handle
      ↓
Java invokes native function
```

Each later chapter isolates one responsibility so failures can be traced to the right contract: memory bounds, lifetime, layout, symbol resolution, or linkage.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ffm-safety-model">The FFM Safety Model</a>

<details>
<summary>Click for details</summary>

FFM adds safety checks around memory that raw pointer APIs typically leave to the programmer.

The two central guarantees are:

- **spatial safety**: access is checked against the bounds of the `MemorySegment`;
- **temporal safety**: access is checked against the lifetime of the segment's scope.

With a confined arena:

```java
MemorySegment segment;

try (Arena arena = Arena.ofConfined()) {
    segment = arena.allocate(16, ValueLayout.JAVA_INT.byteAlignment());
    segment.set(ValueLayout.JAVA_INT, 0, 42);
}

// The arena is closed; the segment is no longer valid.
// segment.get(ValueLayout.JAVA_INT, 0) throws IllegalStateException.
```

FFM also models thread accessibility: memory allocated from a confined arena is restricted to the arena's owner thread, while shared, global, and automatic arena memory can be accessible from multiple threads.

These guarantees apply to Java's access through the FFM abstractions. Once an address is passed to arbitrary native code, that code can still perform unsafe operations beyond what Java can check. FFM narrows the unsafe surface; it does not sandbox the native function.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ffm-restricted-operations">Restricted Operations and Native Access</a>

<details>
<summary>Click for details</summary>

Some FFM operations are classified as **restricted** because incorrect use can bypass the API's normal safety model and cause JVM crashes or silent memory corruption. Java 21 marks such methods explicitly in the API documentation.

Restricted operations are governed by **native access**, which is separate from the fact that FFM itself is a Java 21 preview API.

For a named module:

```text
java --enable-preview \
     --enable-native-access=com.example.nativebridge \
     ...
```

For class-path code, `ALL-UNNAMED` is the relevant target:

```text
java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     ...
```

In Java 21, when the option is omitted, restricted calls are still allowed but emit runtime warnings. If `--enable-native-access` is specified, native access is granted only to the modules listed by that option; a restricted call from a caller without native access is rejected with `IllegalCallerException`. Granting native access to the calling named module, or to `ALL-UNNAMED` for class-path code, suppresses warnings for that authorized caller. This native-access policy is independent from the hard Java 21 requirement to enable preview features before FFM code can compile and run.

Treat restricted methods as an escalation point. Prefer ordinary bounded segments, layouts, arenas, and linker operations when they can represent the native contract without weakening safety guarantees.

The FFM overview now splits into its core responsibilities. The next chapter starts with MemorySegment, because Java first needs a bounded representation of a memory region before lifetime, layout, and foreign-call mechanics can be reasoned about precisely.

</details>

- [Quay lại đầu trang](#back-to-top)
