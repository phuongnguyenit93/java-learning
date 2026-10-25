<a id="back-to-top"></a>

# Class Files, Bytecode, and the Pre-Execution Boundary

## Menu
- [Class File Format Overview](#classfile-format-overview)
- [The ClassFile Structure and Major Components](#classfile-structure)
- [The Bytecode Instruction Model](#bytecode-instruction-model)
- [The Constant Pool and Symbolic References](#constant-pool-symbolic-references)
- [From the Constant Pool to the Runtime Constant Pool](#runtime-constant-pool)
- [Loading, Linking, and Initialization at the JVM Level](#loading-linking-initialization-overview)
- [Verification, Preparation, and Resolution](#verification-preparation-resolution)
- [Failures Before a Method Body Executes](#pre-execution-failures)

## <a id="classfile-format-overview">Class File Format Overview</a>

<details>
<summary>Click for details</summary>

A class file is a tightly specified binary representation. Each valid representation describes one class, interface, or module at a particular class-file format version.

At a high level it contains:

```text
magic + version
constant_pool
access_flags
this_class / super_class / interfaces
fields
methods
attributes
```

The `0xCAFEBABE` magic value identifies the format, while major/minor version values let the JVM determine whether the artifact uses a class-file version it supports.

A class file is not “compressed Java source.” It is a separate representation with its own vocabulary and constraints, designed specifically for JVM execution.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classfile-structure">The ClassFile Structure and Major Components</a>

<details>
<summary>Click for details</summary>

The `ClassFile` structure is a schema-defined sequence. Rather than repeating full names everywhere, many entries reference indexes in the constant pool.

Conceptually:

```text
method_info
  name_index ─────────┐
  descriptor_index ─┐│
                    ↓↓
              constant_pool
```

A non-abstract, non-native executable method normally carries a `Code` attribute containing instruction bytes, max stack/local information, an exception table, and nested attributes such as debug metadata.

This structure is why tools such as `javap -v` can systematically display bytecode, descriptors, constant-pool entries, and method attributes.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instruction-model">The Bytecode Instruction Model</a>

<details>
<summary>Click for details</summary>

Bytecode is the JVM instruction set. Each instruction has an opcode and may carry operands. Instructions commonly operate on local variables, the operand stack, objects/arrays, or symbolic references in the runtime constant pool.

For example:

```java
int add(int a, int b) {
    return a + b;
}
```

can conceptually compile to:

```text
iload_1
iload_2
iadd
ireturn
```

The important lesson is not memorizing opcodes. It is understanding the **stack-oriented execution model**: instructions consume and produce values on the operand stack while reading/writing local slots and runtime data.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constant-pool-symbolic-references">The Constant Pool and Symbolic References</a>

<details>
<summary>Click for details</summary>

The constant pool is a central table in the class file. In addition to literal constants, it stores symbolic information for classes, fields, methods, name-and-type pairs, method handles, dynamic call sites, and other entities.

JVM instructions do not need to embed a final machine address for a method or field:

```text
invokevirtual #12
              ↓
CONSTANT_Methodref
              ↓
class + name + descriptor
```

This symbolic representation means the class file does not need to know physical object layout or the final machine-code address at compile time. Runtime linking/resolution connects symbolic identities to runtime entities.

That makes the constant pool a key bridge between the binary class-file representation and runtime execution.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-constant-pool">From the Constant Pool to the Runtime Constant Pool</a>

<details>
<summary>Click for details</summary>

When the JVM creates a class or interface at runtime, information from its class-file constant pool is used to construct a **runtime constant pool**. This runtime structure serves many of the same purposes as a symbol table in a traditional language implementation.

It can contain:

- static constants ready for use;
- symbolic references that may still require resolution;
- runtime representations used by instruction execution.

The runtime constant pool is therefore a **runtime data structure**, not simply the original bytes sitting in the class file.

Resolution timing is flexible within the specification rules, so you should not assume every symbolic reference is resolved immediately when a class is first loaded.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-linking-initialization-overview">Loading, Linking, and Initialization at the JVM Level</a>

<details>
<summary>Click for details</summary>

Before a class/interface participates fully in execution, the JVM model distinguishes three major lifecycle ideas:

```text
Loading
→ obtain a binary representation and create a runtime class/interface

Linking
→ verification + preparation + possibly resolution

Initialization
→ execute the class/interface initialization method <clinit>
```

Loading asks whether the runtime has created the type representation. Linking asks whether that representation has been validated and integrated into JVM runtime state. Initialization applies the type's static initialization semantics.

This module teaches the lifecycle and failure boundary only. ClassLoader APIs, delegation, and loader identity belong to the Class Loading module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verification-preparation-resolution">Verification, Preparation, and Resolution</a>

<details>
<summary>Click for details</summary>

**Verification** checks that a prospective class representation satisfies structural and type-safety constraints required for safe execution.

**Preparation** creates static-field storage and gives it default JVM values before Java-level initializers run.

**Resolution** turns symbolic references into more direct runtime references to the corresponding class, interface, field, or method.

For example:

```java
static int port = 8080;
```

conceptually separates into:

```text
preparation    → storage exists, initial default value = 0
initialization → initializer executes, value becomes 8080
```

For a `static final` constant variable carrying a `ConstantValue` attribute, the constant value is also assigned by the **initialization procedure**, before `<clinit>` runs; it is still not part of preparation. A durable rule is: preparation creates static storage and installs default JVM values, while Java-level/static constant values belong to initialization semantics.

Separating preparation from initialization explains many static-state behaviors hidden by the source-level view.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pre-execution-failures">Failures Before a Method Body Executes</a>

<details>
<summary>Click for details</summary>

Not every runtime failure happens “inside the method body.” Class-file format checks, verification, linking, or initialization can fail before the intended bytecode begins executing.

Useful failure categories include:

- invalid or unsupported class representation/version;
- verification failure;
- missing or incompatible classes/members during linkage;
- failure while executing static initialization.

A practical example is compiling against one dependency version and running with another. The method call may fail during linkage because a symbolic reference no longer matches the runtime type, before the target method body executes.

Keep this distinction:

```text
successful compilation
≠ guaranteed successful runtime linkage
```

Determining exactly where a class came from belongs to Class Loading/Diagnostics; here the goal is identifying the pre-execution failure boundary.

</details>

- [Quay lại đầu trang](#back-to-top)
