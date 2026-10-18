<a id="back-to-top"></a>

# Dynamic Runtime Mental Model

## Menu
- [What Is Dynamic Runtime and Why Does It Exist?](#dynamic-runtime-purpose)
- [Problems That Need Dynamic Runtime Mechanisms](#dynamic-runtime-problem-space)
- [Direct Calls, Reflection, and java.lang.invoke](#direct-reflection-invoke-boundary)
- [Prerequisites and Module Boundaries](#dynamic-runtime-boundaries)

## <a id="dynamic-runtime-purpose">What Is Dynamic Runtime and Why Does It Exist?</a>

<details>
<summary>Click for details</summary>

Dynamic Runtime in this module means the low-level `java.lang.invoke` mechanisms that let Java represent invocation or variable access as typed runtime values, then link, adapt, or select behavior dynamically. The goal is not to make ordinary application code “dynamic”; it is to understand primitives used by the JVM, frameworks, and language runtimes when a direct Java call is not flexible enough.

The module has two main branches:

```text
typed invocation
MethodType + MethodHandle + Lookup
        ↓
adaptation / composition
        ↓
CallSite + invokedynamic

typed variable access
VarHandle
```

`MethodHandle` and `VarHandle` receive special JVM treatment through signature-polymorphic methods, so the actual call-site descriptor can carry the runtime contract instead of being limited by the apparent Java declaration.

The module follows this learning path:

```text
MethodType + MethodHandle
→ Lookup
→ adaptation / composition
→ CallSite
→ invokedynamic
→ VarHandle
→ synthesis / decision model
```

The invocation/linkage branch comes first so its mental model is complete before VarHandle opens the parallel variable-access branch. By the end, the learner should distinguish **type contracts**, **access capabilities**, **linkage objects**, and **variable-access semantics**, rather than merely memorizing API names.

One important correction to a common mental model: **dynamic does not simply mean “the member is unknown until runtime.”** A member may already be known while a runtime still benefits from a typed executable handle, transferable lookup capability, composable adapters, or a call site whose target can change.

</details>

- [Back to top](#back-to-top)

---

## <a id="dynamic-runtime-problem-space">Problems That Need Dynamic Runtime Mechanisms</a>

<details>
<summary>Click for details</summary>

Suppose a framework must invoke `greet(String)` across many implementations. When application code knows the target type normally, a direct call is still the best option:

```java
String result = greeter.greet("Phuong");
```

The interesting problems start when infrastructure code wants to **store behavior as data**, pre-bind a receiver, normalize several signatures to one internal contract, or link a call site once and later dispatch through its current target. Reflection handles runtime inspection and invocation well, but `java.lang.invoke` adds a different model: typed executable references plus composable transformations.

```text
"The call contract is (String)String"
        ↓
MethodType describes the contract
        ↓
Lookup checks access and creates a MethodHandle
        ↓
MethodHandle can be bound/adapted/composed
        ↓
CallSite can hold a target
        ↓
invokedynamic lets the JVM link through bootstrap logic
```

VarHandle solves a separate problem: representing **a variable or family of variables** together with access semantics such as plain, volatile, acquire/release, or compare-and-set.

</details>

- [Back to top](#back-to-top)

---

## <a id="direct-reflection-invoke-boundary">Direct Calls, Reflection, and java.lang.invoke</a>

<details>
<summary>Click for details</summary>

These mechanisms sit near one another but solve different problems:

| Mechanism | Best fit | Main characteristic |
| --- | --- | --- |
| Direct call | Type and target are ordinary compile-time code | Simplest and clearest |
| Reflection | Runtime metadata/member inspection or metadata-driven invocation | Metadata-first model |
| `java.lang.invoke` | Typed executable handles, adaptation/composition, or runtime linkage | Runtime/JVM-oriented model |

Reflection example:

```java
Method method = Greeter.class.getMethod("greet", String.class);
Object result = method.invoke(greeter, "Phuong");
```

MethodHandle example:

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodType type = MethodType.methodType(String.class, String.class);
MethodHandle handle = lookup.findVirtual(Greeter.class, "greet", type);

String result = (String) handle.invokeExact(greeter, "Phuong");
```

MethodHandle does not replace Reflection. Reflection remains the owner of runtime inspection; this module compares the two only enough to explain why a framework may inspect with Reflection but execute through precomputed MethodHandles.

</details>

- [Back to top](#back-to-top)

---

## <a id="dynamic-runtime-boundaries">Prerequisites and Module Boundaries</a>

<details>
<summary>Click for details</summary>

Recommended prerequisites:

- Java types, methods, fields, constructors, and exceptions;
- Reflection at the `Class`, `Method`, and `Field` level;
- JVM concepts such as bytecode, method descriptors, and linkage;
- concurrency fundamentals before reasoning deeply about `MutableCallSite` visibility or VarHandle memory ordering.

This module **owns**:

- `MethodType`, `MethodHandle`, and signature polymorphism;
- `MethodHandles.Lookup` and access capabilities;
- MethodHandle adaptation and composition;
- `CallSite`, bootstrap linkage, and `invokedynamic`;
- `VarHandle` as a typed variable-access primitive.

It **hands off**:

- full metadata/member inspection → Reflection;
- constant-pool structure, bytecode execution, and JVM resolution rules → JVM;
- happens-before/JMM and lock-free algorithm design → Concurrency;
- JNI/FFM/native memory → Native Interoperability;
- release-by-release history → Java Version.

As you continue, keep asking: **which part of runtime behavior does this primitive own, and which part belongs to a neighboring module?**

</details>

- [Back to top](#back-to-top)
