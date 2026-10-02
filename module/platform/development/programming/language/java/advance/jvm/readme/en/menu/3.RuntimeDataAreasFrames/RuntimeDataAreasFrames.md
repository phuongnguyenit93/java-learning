<a id="back-to-top"></a>

# Runtime Data Areas and Stack Frames

## Menu
- [Why Runtime Data Areas Exist](#runtime-data-areas-purpose)
- [VM-Wide vs Thread-Private Areas](#vm-wide-vs-thread-private-areas)
- [The PC Register](#pc-register)
- [JVM Stacks](#jvm-stacks)
- [The Heap](#heap)
- [The Method Area](#method-area)
- [The Runtime Constant Pool in the Runtime Data Model](#runtime-constant-pool-area)
- [Native Method Stacks](#native-method-stacks)
- [The Stack Frame Model](#stack-frame-model)
- [Local Variables and the Operand Stack](#local-variables-and-operand-stack)
- [Frame Lifecycle, Method Return, and Abrupt Completion](#frame-lifecycle-and-completion)
- [StackOverflowError, OutOfMemoryError, and Resource Boundaries](#runtime-area-failure-boundaries)

## <a id="runtime-data-areas-purpose">Why Runtime Data Areas Exist</a>

<details>
<summary>Click for details</summary>

While a program runs, the JVM needs places to hold code-related state, objects, call-stack state, and intermediate values. The JVM Specification describes these **runtime data areas** as a logical execution contract. It does not require one universal physical layout inside process memory.

Two questions organize the model:

```text
Is this state VM-wide or thread-private?
```

and:

```text
Is this a specification-level area,
or an implementation-specific memory structure?
```

Without that distinction, it is easy to make incorrect statements such as “the method area is exactly Metaspace” or “a JVM stack has one fixed physical layout.”

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="vm-wide-vs-thread-private-areas">VM-Wide vs Thread-Private Areas</a>

<details>
<summary>Click for details</summary>

Some runtime areas exist for the VM as a whole while others belong to individual threads.

```text
VM-wide/shared
├─ Heap
├─ Method Area
└─ Runtime Constant Pools (associated with classes/interfaces)

Per-thread
├─ pc Register
├─ JVM Stack
└─ Native Method Stack (when an implementation uses one)
```

This ownership distinction helps explain lifetime and visibility of runtime state. A local variable belongs to a frame on one thread's JVM stack, while an object referenced by that local variable normally lives on the shared heap.

This is not the Java Memory Model. Shared-vs-thread-private here is a **runtime storage model**; happens-before and inter-thread visibility belong to Concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pc-register">The PC Register</a>

<details>
<summary>Click for details</summary>

Each JVM thread has its own `pc` (program counter) register. While a thread executes a non-native method, this register represents the current JVM instruction position according to the implementation's execution model.

It exists because the runtime needs to know where a thread is in its instruction stream so execution can proceed through branches, calls, and scheduling transitions.

For a native method, the specification does not define the `pc` value in the same way.

Application code does not manipulate the JVM `pc` directly; this concept completes the picture of per-thread execution state beyond just “stack and heap.”

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-stacks">JVM Stacks</a>

<details>
<summary>Click for details</summary>

Each JVM thread has its own JVM stack. The stack primarily contains **frames** for the thread's active method invocations.

```text
Thread A JVM Stack
┌─────────────────┐
│ methodC frame   │ ← current
├─────────────────┤
│ methodB frame   │
├─────────────────┤
│ methodA frame   │
└─────────────────┘
```

A new frame is created when a method is invoked and destroyed when that invocation completes. Because the stack is thread-private, a local reference is not automatically shared state even when the object it points to is on the shared heap.

Stack size may be fixed or dynamically expanded depending on the implementation. The specification defines semantics and failure boundaries rather than one physical layout.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap">The Heap</a>

<details>
<summary>Click for details</summary>

The heap is a shared runtime data area from which storage for objects and arrays is allocated. It is created when the JVM starts and is managed by an automatic storage-management system commonly called the garbage collector.

An important boundary is:

> The JVM Specification does not require a particular object placement strategy, generation layout, or garbage-collection algorithm.

The heap can be fixed or expandable depending on the implementation. If enough storage cannot be provided, execution may fail with `OutOfMemoryError`.

The next chapter goes deeper into allocation, reachability, and garbage collection. Here the goal is placing the heap correctly inside the runtime-data-area model.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-area">The Method Area</a>

<details>
<summary>Click for details</summary>

The method area is a shared runtime data area that stores structures associated with classes and interfaces, including runtime constant pools and method/field-related runtime information.

The specification defines a **logical responsibility**, not a required physical region literally named “method area.”

In modern HotSpot discussions you will often see **Metaspace** associated with class metadata. Avoid treating the two as identical:

```text
method area
→ specification-level abstraction

Metaspace
→ part of HotSpot's implementation strategy for class metadata
```

This distinction remains important across JVM vendors and versions.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-constant-pool-area">The Runtime Constant Pool in the Runtime Data Model</a>

<details>
<summary>Click for details</summary>

Each class or interface has a runtime constant pool created from information in its class-file constant pool. In the JVMS model, it belongs to method-area-level runtime state.

It connects the file representation to execution:

```text
class-file constant_pool
        ↓ class creation
runtime constant pool
        ↓ resolution/execution
runtime behavior
```

That is why the constant pool appears in both the class-file chapter and the runtime-data chapter: the concept has **two lifecycle representations**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-method-stacks">Native Method Stacks</a>

<details>
<summary>Click for details</summary>

A JVM implementation may use native method stacks to support methods implemented in native code or other native runtime mechanisms.

The specification gives implementations freedom in how this resource is organized; an implementation that does not support native methods in that form may not need a distinct native method stack.

The JVM lesson here is simply that Java application execution can require stack/resources outside the JVM-frame model. JNI and FFM programming are owned by Native Interoperability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-frame-model">The Stack Frame Model</a>

<details>
<summary>Click for details</summary>

A frame is the execution context for **one method invocation**. The JVMS model gives each frame three major components:

```text
Frame
├─ local variables
├─ operand stack
└─ reference to the current class's runtime constant pool
```

A frame also participates in dynamic linking, method return, and exception dispatch.

The required local-variable and operand-stack sizes are known from class-file method metadata, allowing the runtime to create an appropriate frame for an invocation.

This matters because JVM bytecode primarily manipulates local slots and the operand stack, not source-language variables directly.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="local-variables-and-operand-stack">Local Variables and the Operand Stack</a>

<details>
<summary>Click for details</summary>

The local-variable array provides indexed slots for parameters and local method state. The operand stack is the working stack on which many JVM instructions consume inputs and produce results.

```text
iload_1   local[1] → operand stack
iload_2   local[2] → operand stack
iadd      pop two ints, push result
ireturn   pop result and return
```

After JIT optimization, a source-level value may not map one-to-one to a physical variable or register. But at the class-file/JVMS level, local variables and the operand stack are the core execution model.

That is why `javap -c` is useful for learning: you can follow value flow without needing to know the CPU's final register allocation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="frame-lifecycle-and-completion">Frame Lifecycle, Method Return, and Abrupt Completion</a>

<details>
<summary>Click for details</summary>

A frame is created each time a method is invoked and destroyed when the invocation completes.

Completion can be:

- **normal**: the method returns normally, optionally returning a value to its caller;
- **abrupt**: the method throws an exception that is not handled within that invocation.

When an exception propagates, the current frame may be discarded while the runtime searches caller frames for a matching handler.

```text
caller frame
   ↓ invokes
callee frame
   ├─ normal return → result to caller
   └─ uncaught exception → discard callee frame, propagate
```

Frame lifecycle is therefore tightly connected to both call-stack behavior and exception propagation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-area-failure-boundaries">StackOverflowError, OutOfMemoryError, and Resource Boundaries</a>

<details>
<summary>Click for details</summary>

Runtime areas are backed by finite resources, so the JVM model defines important failure boundaries.

`StackOverflowError` commonly occurs when a thread needs additional stack/frame capacity beyond what can be provided:

```java
static void recurse() {
    recurse();
}
```

`OutOfMemoryError` is broader than “the Java heap is full.” It can represent failure to obtain memory for heap or other runtime structures/resources.

The practical rule is:

```text
error type/message
≠ complete production root cause
```

The JVM module explains the mechanism; heap dumps, NMT, JFR, and evidence-driven root-cause analysis belong to Runtime Diagnostics.

</details>

- [Quay lại đầu trang](#back-to-top)
