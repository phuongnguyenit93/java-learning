<a id="back-to-top"></a>

# Linker, SymbolLookup, and Function Descriptors

## Menu
- [How Does Linker Connect Java to Native Functions?](#linker-model)
- [SymbolLookup and Native Symbol Resolution](#symbol-lookup)
- [Library-Specific Symbol Lookup](#library-symbol-lookup)
- [FunctionDescriptor and Native Function Signatures](#function-descriptor)
- [Linker and ABI Mapping](#linker-abi-mapping)
- [The MethodHandle Boundary with Dynamic Runtime](#method-handle-boundary)
- [jextract and Generated Bindings](#jextract-generated-bindings)

## <a id="linker-model">How Does Linker Connect Java to Native Functions?</a>

<details>
<summary>Click for details</summary>

A `Linker` connects Java invocation to a foreign function ABI. For the current operating system and processor, `Linker.nativeLinker()` returns the linker that understands the platform's native calling conventions and supported native layouts.

The linker does not discover a function by itself. It needs:

- an address identifying the foreign entry point;
- a `FunctionDescriptor` describing its parameter and return layouts.

The usual flow is:

```text
find native symbol
      ↓
obtain symbol address
      ↓
describe function signature
      ↓
Linker creates downcall MethodHandle
      ↓
invoke using Java carriers derived from the descriptor
```

The reverse direction uses the same linker to build an upcall stub so native code can invoke a Java `MethodHandle` through a function pointer.

The linker therefore owns ABI adaptation at the Java/native call boundary. It does not replace the need to describe the native signature correctly.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symbol-lookup">SymbolLookup and Native Symbol Resolution</a>

<details>
<summary>Click for details</summary>

A `SymbolLookup` resolves a native symbol name to an address represented as a zero-length native `MemorySegment`.

```java
Linker linker = Linker.nativeLinker();

MemorySegment strlen = linker.defaultLookup()
    .find("strlen")
    .orElseThrow();
```

The lookup answers **where is the symbol?** It does not tell Java the function's argument or return types. That second part belongs to `FunctionDescriptor`.

The zero-length symbol segment can be supplied to `Linker.downcallHandle(...)` as the target address.

Different lookup sources have different visibility:

- `linker.defaultLookup()` searches symbols in the implementation-defined set of commonly used native libraries for that linker;
- `SymbolLookup.loaderLookup()` searches libraries associated with the caller's class loader;
- `SymbolLookup.libraryLookup(...)` creates a lookup for a specific library.

Do not assume a symbol exposed by one platform's default lookup is portable to every supported platform.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="library-symbol-lookup">Library-Specific Symbol Lookup</a>

<details>
<summary>Click for details</summary>

`SymbolLookup.libraryLookup` loads, if necessary, a specific native library and creates a lookup tied to an `Arena`.

```java
try (Arena arena = Arena.ofConfined()) {
    SymbolLookup lookup =
        SymbolLookup.libraryLookup("native_math", arena);

    MemorySegment add = lookup.find("add").orElseThrow();
}
```

The arena is part of the lifetime contract. With a closeable arena, closing the arena ends the lifetime associated with the lookup and its symbol segments; the corresponding library can then be unloaded according to the API contract.

There are overloads accepting a library name or a `Path`. Library-name resolution remains operating-system specific.

In Java 21, `libraryLookup` is a restricted operation because loading arbitrary native code is inherently unsafe. Preview enablement and native-access warning control remain separate concerns.

Use a library-specific lookup when the integration depends on a known native dependency rather than on whichever symbols happen to be exposed by the linker's default lookup.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="function-descriptor">FunctionDescriptor and Native Function Signatures</a>

<details>
<summary>Click for details</summary>

A `FunctionDescriptor` models a foreign function signature as zero or more argument layouts plus zero or one return layout.

For a C function:

```c
int add(int left, int right);
```

a corresponding descriptor can be:

```java
FunctionDescriptor ADD = FunctionDescriptor.of(
    ValueLayout.JAVA_INT,
    ValueLayout.JAVA_INT,
    ValueLayout.JAVA_INT
);
```

For `void` return types, use `FunctionDescriptor.ofVoid(...)`.

The descriptor is a binary contract, not a Java reflection signature. Its layouts tell the linker how native arguments and results are represented according to the ABI.

Address parameters use an `AddressLayout` such as `ValueLayout.ADDRESS`. Struct or union values may use group layouts when supported by the native linker.

An incorrect descriptor can create a call handle that compiles on the Java side yet invokes native code with the wrong binary contract, which can lead to process corruption or a crash.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linker-abi-mapping">Linker and ABI Mapping</a>

<details>
<summary>Click for details</summary>

The native linker knows the ABI of the platform on which the JVM is currently running. It uses that knowledge to translate the layouts in a function descriptor into the machine-level calling convention.

That adaptation can include details such as:

- which argument carriers go in registers or stack slots;
- how return values are represented;
- how pointer-sized values are passed;
- how supported struct/union layouts are classified by the ABI.

This is why a descriptor is expressed in memory layouts rather than only Java classes.

```text
Java carriers
      ↓
FunctionDescriptor layouts
      ↓
native Linker
      ↓
current OS/CPU ABI
      ↓
foreign function
```

`Linker.nativeLinker()` only represents the underlying native platform. It is not a cross-compiler that lets one JVM invoke a function using an unrelated target platform ABI.

Correct layout selection remains the application's responsibility.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handle-boundary">The MethodHandle Boundary with Dynamic Runtime</a>

<details>
<summary>Click for details</summary>

FFM uses `MethodHandle` as the Java invocation shape for downcalls and as the Java target shape for upcalls.

For a downcall:

```java
MethodHandle add = linker.downcallHandle(
    symbol,
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT
    )
);

int result = (int) add.invokeExact(2, 3);
```

The handle's Java method type is derived from the descriptor's layouts and linker rules. That typed invocation bridge is why FFM naturally interacts with `java.lang.invoke`.

This module only needs the boundary:

```text
FFM owns
→ how a foreign signature becomes a callable/callback boundary

Dynamic Runtime owns
→ deep MethodHandle lookup, adaptation, composition, invocation mechanics
```

Do not duplicate the full `MethodHandle` curriculum here. The interoperability concern is that the handle type and the foreign descriptor must remain compatible.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jextract-generated-bindings">jextract and Generated Bindings</a>

<details>
<summary>Click for details</summary>

`jextract` is tooling that can read native C headers and generate Java bindings based on FFM concepts. Its purpose is to remove repetitive hand-written declarations for large native APIs.

Without generated bindings, application code may manually define:

- layouts for native declarations;
- symbol names;
- function descriptors;
- helper methods around downcall handles;
- constants and type aliases represented in the header.

Generated bindings can make a large API easier to consume and reduce transcription mistakes. They do not remove the underlying ABI, lifetime, or safety rules.

Treat `jextract` as **supporting tooling**, not as the core FFM abstraction:

```text
FFM API
→ conceptual/runtime contract

jextract
→ code generation that materializes bindings for a native header
```

When debugging generated code, you still need to understand `MemorySegment`, layouts, symbol lookup, descriptors, and linker calls. Tool/version details may evolve independently from the Java 21 API baseline.

With symbol resolution and function signatures established, the next chapter executes the boundary in both directions: downcalls from Java to native code and upcalls where native code invokes a Java callback.

</details>

- [Quay lại đầu trang](#back-to-top)
