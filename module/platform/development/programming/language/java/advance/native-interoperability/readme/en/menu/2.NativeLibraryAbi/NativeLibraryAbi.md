<a id="back-to-top"></a>

# Native Libraries, ABI, and Linkage

## Menu
- [The Native Library Model in Java](#native-library-model)
- [Locating and Loading Native Libraries](#native-library-loading)
- [System.load and System.loadLibrary](#system-load-vs-loadlibrary)
- [Symbols and Native Linkage](#native-symbol-resolution)
- [ABI and Calling Conventions](#abi-calling-convention)
- [OS, CPU Architecture, and Binary Compatibility](#platform-binary-compatibility)

## <a id="native-library-model">The Native Library Model in Java</a>

<details>
<summary>Click for details</summary>

A native library is a binary artifact loaded into the current process so code in that process can call exported machine-code entry points. The exact file format depends on the platform: for example, Windows commonly uses DLLs, Linux uses ELF shared objects, and macOS uses Mach-O dynamic libraries.

Java does not call a library “by file contents.” The important runtime sequence is:

```text
native library file
      ↓ load into process
exported symbols become available
      ↓ resolve a symbol
native address / entry point
      ↓ call using the correct ABI
native function executes
```

JNI often loads a library that contains implementations of Java `native` methods. FFM can also work with loaded libraries, but resolves symbols through `SymbolLookup` and creates call handles through `Linker`.

The library is only one part of the contract. A successful load does not prove that the expected symbol exists or that Java is using the correct function signature. Those are separate linkage and ABI concerns.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-library-loading">Locating and Loading Native Libraries</a>

<details>
<summary>Click for details</summary>

Before Java can call native code, the operating system's loader must be able to find a compatible binary and load it into the process. Search behavior depends on how the library is requested and on the operating system.

For JNI-style loading, `System.loadLibrary("mathbridge")` asks the runtime to load a logical library name. The runtime maps that logical name to a platform-specific file name and searches configured native-library locations. `java.library.path` is one Java-side input to that search, while the OS may also have its own loader paths and dependency rules.

Loading can fail even when the primary file exists. A library may depend on another native library that is missing, built for the wrong architecture, or unavailable in the loader's dependency search path.

When diagnosing a load failure, separate these questions:

1. Did Java resolve the requested library name/path?
2. Did the OS loader accept the binary format and architecture?
3. Were the library's native dependencies available?
4. After loading, does the expected symbol exist?

That separation prevents treating every `UnsatisfiedLinkError` as the same problem.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="system-load-vs-loadlibrary">System.load and System.loadLibrary</a>

<details>
<summary>Click for details</summary>

`System.load` and `System.loadLibrary` both load native code into the current process, but they express the target differently.

```java
System.load("C:\\app\\native\\bridge.dll");   // concrete file path
System.loadLibrary("bridge");                  // logical library name
```

`System.load(String filename)` requires an **absolute path** to the native library file. It is useful when application code already knows the exact native artifact to load.

`System.loadLibrary(String libname)` accepts a logical library name without a platform file-name prefix/suffix. The runtime maps that name to a platform representation and searches the native-library path. This is usually better when packaging provides one platform-specific implementation under a stable logical name.

Neither API solves ABI compatibility. A file can be found yet still fail to load because it targets a different CPU architecture, or it can load successfully yet expose an unexpected set of symbols.

Keep library selection/deployment separate from function signature correctness. The former gets code into the process; the latter determines whether calls are valid.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-symbol-resolution">Symbols and Native Linkage</a>

<details>
<summary>Click for details</summary>

A **symbol** is a linker-visible name associated with an address or object in native code. For foreign-function calls, Java usually cares about exported function symbols.

Suppose a C library exports:

```c
int add(int a, int b);
```

At runtime, interoperability code needs two independent pieces of information:

```text
"add"  ----------------------> which native entry point?
int(int, int) + ABI details -> how may that entry point be called?
```

JNI native-method binding performs its own mapping between Java native methods and native implementations, either through name-based linkage or explicit registration. FFM exposes the steps more directly: `SymbolLookup` finds a symbol and `Linker` combines its address with a `FunctionDescriptor`.

Symbol names can be affected by the native toolchain. C++ name mangling is a classic example: a source-level function name may not be exported as a simple C symbol unless the native API deliberately exposes a stable C ABI.

Linkage therefore means more than “the file loaded.” It is the process of connecting the Java-side call site to the intended native entry point under a compatible binary contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abi-calling-convention">ABI and Calling Conventions</a>

<details>
<summary>Click for details</summary>

An ABI, or Application Binary Interface, defines the machine-level contract between independently compiled pieces of code. Source types alone are not enough. The caller and callee must agree on details such as argument placement, return-value representation, register/stack usage, alignment, structure layout rules, and the platform calling convention.

For example, these source declarations look simple:

```c
long sum(long a, long b);
```

but the width of C `long` is platform-dependent. A Java `long` is always 64 bits, so blindly mapping one to the other can be wrong on a platform where native `long` is 32 bits.

FFM's native `Linker` exists to adapt Java carriers and memory layouts to the ABI supported by the current platform. JNI defines its own set of JNI primitive types such as `jint` and `jlong` to make the Java/JNI contract explicit.

The practical rule is: derive layouts and descriptors from the native ABI you are actually calling. Do not infer them only from a Java type with a similar name.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="platform-binary-compatibility">OS, CPU Architecture, and Binary Compatibility</a>

<details>
<summary>Click for details</summary>

Native binaries are normally platform artifacts. Compatibility depends on more than the library's logical name.

At minimum, check:

- operating-system family and binary format;
- CPU architecture such as x86-64 versus AArch64;
- ABI/calling convention;
- transitive native dependencies;
- vendor/runtime assumptions made by the library;
- exported symbol set and version.

A 64-bit x86 Windows DLL cannot be loaded into an AArch64 Linux process just because both expose a function named `add`. The machine code, object format, loader, and ABI are different.

This is why native integrations often need platform-specific packaging:

```text
native/
  windows-x86_64/...
  linux-x86_64/...
  linux-aarch64/...
  macos-aarch64/...
```

Java bytecode may be portable across those environments, while the native payload is not. Treat the native artifact matrix as part of the application's deployment contract and test it explicitly in the environments you claim to support.

With the library, symbol, and ABI contract established, the next chapter can focus on JNI: the traditional JVM bridge that applies those native contracts to Java methods, references, exceptions, and threads.

</details>

- [Quay lại đầu trang](#back-to-top)
