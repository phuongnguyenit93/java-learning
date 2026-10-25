<a id="back-to-top"></a>

# Downcalls, Upcalls, and Callback Boundaries

## Menu
- [Downcalls: Java Calling Foreign Functions](#downcall-model)
- [Creating and Using Downcall MethodHandles](#downcall-handle)
- [Arguments, Return Values, and Carrier Mapping](#native-argument-return)
- [Upcalls: Native Code Calling Back into Java](#upcall-model)
- [Upcall Stubs and Executable Native Addresses](#upcall-stub)
- [Callback Lifetime and Resource Ownership](#callback-lifetime)
- [Thread Boundaries When Native Code Calls Back into Java](#callback-thread-boundary)

## <a id="downcall-model">Downcalls: Java Calling Foreign Functions</a>

<details>
<summary>Click for details</summary>

A **downcall** is a call from Java into a foreign function. FFM represents the call site as a `MethodHandle` created by a `Linker`.

The call depends on three contracts:

```text
symbol address
→ which native function?

FunctionDescriptor
→ what argument/return layouts?

native Linker
→ how does this platform ABI perform the call?
```

For example, a native `strlen`-like function can be linked after resolving its symbol and describing its signature.

The downcall boundary is where Java values become ABI carriers and native execution begins. Once control enters the foreign function, the JVM cannot enforce the foreign implementation's internal pointer arithmetic or memory safety.

Treat a downcall as a typed but still native boundary: Java has a callable handle, yet correctness ultimately depends on the descriptor matching the real foreign function.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="downcall-handle">Creating and Using Downcall MethodHandles</a>

<details>
<summary>Click for details</summary>

A bound downcall handle is created by combining a function address with a descriptor:

```java
Linker linker = Linker.nativeLinker();
MemorySegment symbol = linker.defaultLookup()
    .find("strlen")
    .orElseThrow();

MethodHandle strlen = linker.downcallHandle(
    symbol,
    FunctionDescriptor.of(
        ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS
    )
);
```

The returned `MethodHandle` has a Java method type derived from the descriptor and linker rules.

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment text = arena.allocateUtf8String("hello");
    long length = (long) strlen.invokeExact(text);
}
```

The exact native layout for a type such as C `size_t` is platform-dependent, so production descriptors should be derived from the actual target ABI rather than copied blindly from an example.

The linker also has an unbound downcall form whose resulting handle accepts the target address as a leading argument, useful when the function pointer varies at invocation time.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-argument-return">Arguments, Return Values, and Carrier Mapping</a>

<details>
<summary>Click for details</summary>

Arguments and return values cross the FFM boundary through **carrier types** derived from the layouts in the `FunctionDescriptor`.

Typical relationships include:

| Layout | Java carrier shape |
| --- | --- |
| integer `ValueLayout` | matching Java primitive |
| floating-point `ValueLayout` | matching Java primitive |
| `AddressLayout` | `MemorySegment` representing an address |
| supported group layout | `MemorySegment` representing structured value storage |

This is why the descriptor and the method handle type are connected. If the descriptor says an argument is an address, the Java invocation supplies a `MemorySegment`, not a raw Java `long` pretending to be a pointer.

Pointer-returning functions deserve special care. In Java 21, when a downcall return layout is a plain `AddressLayout`, the returned native `MemorySegment` has size `0` and a fresh scope that is always alive. If that return `AddressLayout` has a target layout `T`, the returned segment size is `T.byteSize()`. Those Java-side bounds/scope semantics still do **not** prove that the native allocation is actually alive, owned by Java, or safe for that duration. Establishing a usable region therefore requires the real native size/lifetime contract and can require restricted `reinterpret(...)` when Java must attach different bounds or lifetime management.

Carrier compatibility is necessary but not sufficient: the native function's real ABI signature still has to match the descriptor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upcall-model">Upcalls: Native Code Calling Back into Java</a>

<details>
<summary>Click for details</summary>

An **upcall** reverses the direction: native code calls a Java target through a native function pointer.

This is useful for native APIs that accept callbacks, such as comparators, event handlers, visitor functions, or completion functions.

The conceptual flow is:

```text
Java MethodHandle
      ↓
FunctionDescriptor for callback ABI
      ↓
Linker.upcallStub(...)
      ↓
native function pointer
      ↓
foreign code invokes pointer
      ↓
JVM executes Java target
```

The descriptor must match both sides: the native callback type and the Java target method-handle type.

Upcalls introduce an extra responsibility that ordinary downcalls do not: the function pointer may be stored by native code and invoked later. That makes callback lifetime and thread assumptions part of the integration contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upcall-stub">Upcall Stubs and Executable Native Addresses</a>

<details>
<summary>Click for details</summary>

`Linker.upcallStub(...)` creates executable native code that adapts a foreign callback invocation to a Java `MethodHandle` target.

```java
MemorySegment callback = linker.upcallStub(
    targetHandle,
    callbackDescriptor,
    arena
);
```

The returned `MemorySegment` is a zero-length segment whose address is the native function pointer. Native code can receive that address as a callback argument.

The stub is associated with the supplied `Arena`. Its executable storage remains valid only for that lifetime.

The Java target must not let exceptions escape through the upcall boundary. Java 21's linker contract is categorical: if the target `MethodHandle` throws during an upcall, the JVM **will terminate abruptly**. Handle failures inside the callback and translate them into a native-compatible result/status.

Creating an upcall stub is a restricted operation in Java 21 because an invalid function-pointer contract can crash the process.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="callback-lifetime">Callback Lifetime and Resource Ownership</a>

<details>
<summary>Click for details</summary>

The most important callback question is: **how long may native code retain and invoke this function pointer?**

Because an upcall stub is tied to an arena, closing that arena invalidates the stub. Passing its address to native code that stores it beyond the arena lifetime creates a dangling callback.

Unsafe lifecycle:

```text
create confined arena
      ↓
create upcall stub
      ↓
native library stores callback pointer
      ↓
close arena
      ↓
native library calls old pointer later
      ↓
invalid execution / crash risk
```

The arena lifetime must cover every possible native invocation of the callback. If the library has explicit register/unregister operations, unregister first, ensure callbacks can no longer arrive, then close the arena.

Also track any Java state captured by or reachable from the callback target. Callback ownership is not only the executable stub; it includes the resources the Java target needs to remain valid.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="callback-thread-boundary">Thread Boundaries When Native Code Calls Back into Java</a>

<details>
<summary>Click for details</summary>

A native library may invoke a callback on a thread different from the Java thread that originally registered it. Treat that possibility as part of the callback contract unless the native API guarantees otherwise.

This matters because callback code can touch:

- Java state that requires synchronization;
- thread-local assumptions;
- `MemorySegment` values whose arena restricts thread access;
- native resources that have their own concurrency rules.

A confined segment owned by the registration thread is not automatically legal to access from a callback arriving on another thread. FFM thread-access checks can reject such access with `WrongThreadException`.

```text
native callback thread
      ↓
upcall stub
      ↓
Java callback
      ↓
only use state/resources valid for this thread and lifetime
```

If callbacks may be concurrent, design synchronization explicitly. If Java work must run on a particular executor/thread, make the callback a narrow handoff rather than doing arbitrary thread-sensitive work directly inside the upcall.

Being able to make calls and callbacks is not enough for a safe integration. The next chapter consolidates the failure model—ownership leaks, loading/linkage errors, invalid lifetimes, descriptor mismatches, native error channels, and crash/corruption risk.

</details>

- [Quay lại đầu trang](#back-to-top)
