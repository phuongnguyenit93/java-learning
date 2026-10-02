<a id="back-to-top"></a>

# Choosing and Reasoning About Dynamic Runtime

## Menu
- [Choosing Direct Calls, Reflection, or MethodHandle](#choose-invocation-mechanism)
- [When Should You Use VarHandle?](#choose-variable-access-mechanism)
- [Dynamic Runtime Failure Model](#dynamic-runtime-failure-model)
- [Dynamic Linkage End to End](#dynamic-linkage-end-to-end)
- [Runtime, Framework, and Infrastructure Use Cases](#runtime-framework-use-cases)
- [Handoffs to JVM, Concurrency, and Native Interoperability](#dynamic-runtime-handoffs)

## <a id="choose-invocation-mechanism">Choosing Direct Calls, Reflection, or MethodHandle</a>

<details>
<summary>Click for details</summary>

A useful decision rule:

```text
Target is known and an ordinary Java call works?
→ direct call

Need runtime metadata/member/annotation inspection?
→ Reflection

Need a typed executable reference,
adaptable/composable pipeline,
or runtime linkage primitive?
→ MethodHandle / java.lang.invoke
```

Do not introduce MethodHandle just to replace a normal call:

```java
service.process(request);
```

is still better than a lookup/invoke pipeline when no runtime requirement justifies the added complexity.

MethodHandle often makes sense in frameworks, runtimes, serializers, dynamic-language support, FFM plumbing, or generated dispatch layers where representing behavior as an executable value is part of the design.

</details>

- [Back to top](#back-to-top)

---

## <a id="choose-variable-access-mechanism">When Should You Use VarHandle?</a>

<details>
<summary>Click for details</summary>

Use VarHandle when the problem is variable abstraction plus explicit access semantics:

```text
ordinary field access is enough?
→ direct field/getter/setter access

runtime-selected field/array coordinate
or explicit access mode/CAS?
→ VarHandle
```

Good fits include:

- infrastructure libraries that pre-resolve field access;
- typed array or byte-buffer views;
- implementations of concurrency primitives that require acquire/release/CAS semantics.

Poor fits include:

- ordinary business-entity getters/setters;
- using VarHandle for “performance” without measurement;
- hiding unclear shared-state ownership behind atomic operations.

</details>

- [Back to top](#back-to-top)

---

## <a id="dynamic-runtime-failure-model">Dynamic Runtime Failure Model</a>

<details>
<summary>Click for details</summary>

Failures can be grouped by lifecycle:

```text
LOOKUP
NoSuchMethodException / NoSuchFieldException
IllegalAccessException

TYPE / ADAPTATION
WrongMethodTypeException
ClassCastException on relevant conversion paths

CALL SITE TARGET UPDATE
WrongMethodTypeException
→ setTarget receives a MethodHandle whose type differs from site.type()

BOOTSTRAP / LINKAGE
BootstrapMethodError
→ non-Error bootstrap failure or null/wrong-type CallSite
Error subclass
→ propagated directly from bootstrap/resolution

CONCURRENCY / VISIBILITY
stale MutableCallSite target
VarHandle ordering misuse
```

Debug in flow order rather than adding random casts:

1. is the symbolic member identity correct?
2. does the Lookup have the required capability?
3. what is the current `MethodType`?
4. how does each adapter change the type?
5. does CallSite preserve its type invariant?
6. in multithreaded code, what visibility contract is being used?

</details>

- [Back to top](#back-to-top)

---

## <a id="dynamic-linkage-end-to-end">Dynamic Linkage End to End</a>

<details>
<summary>Click for details</summary>

End-to-end dynamic linkage:

```text
caller
  ↓
invokedynamic descriptor
  ↓
bootstrap(
    Lookup,
    name,
    MethodType,
    static args
)
  ↓
resolve/create MethodHandle target
  ↓
adapt target to exact call-site type
  ↓
create CallSite
  ↓
JVM links instruction to CallSite
  ↓
invoke current target
```

For a mutable call site:

```text
same linked CallSite
        ↓
target changes
        ↓
future invocations may observe the new target
```

This is the core synthesis of the invocation/linkage branch.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-framework-use-cases">Runtime, Framework, and Infrastructure Use Cases</a>

<details>
<summary>Click for details</summary>

Common consumers include:

- lambda metafactories and language runtimes;
- string-concatenation runtime support;
- ORM/serializer/framework dispatch optimization;
- dependency-injection or reflection-reduction hot paths;
- proxy/adapter infrastructure;
- Foreign Function & Memory plumbing that uses MethodHandle;
- dynamic-language implementations.

But “a framework uses MethodHandle” does not mean application code should use it directly.

A healthy design often hides `java.lang.invoke` behind an abstraction:

```text
application code
→ framework contract
→ precomputed MethodHandle/VarHandle pipeline
→ runtime execution
```

The complexity stays in the infrastructure layer where it creates value.

</details>

- [Back to top](#back-to-top)

---

## <a id="dynamic-runtime-handoffs">Handoffs to JVM, Concurrency, and Native Interoperability</a>

<details>
<summary>Click for details</summary>

Major handoffs:

| When the question becomes... | Continue with |
| --- | --- |
| How do constant pool, resolution, bytecode execution, or JIT work? | JVM |
| How do `Class`, `Method`, and annotation inspection work? | Reflection |
| Are acquire/release/happens-before/CAS algorithms correct? | Concurrency |
| How do MethodHandles invoke native functions or access off-heap memory? | Native Interoperability |
| In which Java release did this feature appear/change? | Java Version |
| How do agents rewrite classes at runtime? | Instrumentation |

By the end of the module, you should be able to read a `Lookup → MethodHandle → adaptation → CallSite → invokedynamic` pipeline and recognize VarHandle as a separate variable-access branch.

More important than memorizing API names is identifying **the type contract, access capability, linkage object, and memory-semantics boundary** in a runtime design.

</details>

- [Back to top](#back-to-top)
