<a id="back-to-top"></a>

# VarHandle and Variable Access Semantics

## Menu
- [What Is VarHandle and Why Does It Exist?](#var-handle-purpose)
- [Variable Type and Coordinate Types](#variable-and-coordinate-types)
- [Creating VarHandles for Fields, Array Elements, and byte[]/ByteBuffer Views](#var-handle-creation)
- [Access-Mode Families](#var-handle-access-modes)
- [Access-Mode Types and Signature Polymorphism](#var-handle-access-mode-type)
- [Atomic Updates and the Compare-and-Set Family](#var-handle-atomic-updates)
- [Boundary with the Java Memory Model](#var-handle-memory-model-boundary)

## <a id="var-handle-purpose">What Is VarHandle and Why Does It Exist?</a>

<details>
<summary>Click for details</summary>

`VarHandle` is a **dynamically strongly typed reference** to a variable or parametrically defined family of variables. It provides one abstraction for multiple kinds of variable access while exposing explicit access semantics.

The referenced variable may be:

- a static field;
- an instance field;
- an array element;
- a component viewed through a `byte[]` or `ByteBuffer`;
- a variable accessed by another API that consumes VarHandle, including some foreign-memory use cases.

VarHandles are immutable and have no visible mutable state.

```text
MethodHandle
→ executable operation

VarHandle
→ variable location/family
   + access-mode semantics
```

</details>

- [Back to top](#back-to-top)

---

## <a id="variable-and-coordinate-types">Variable Type and Coordinate Types</a>

<details>
<summary>Click for details</summary>

Every VarHandle has:

- a **variable type** `T`: the type stored in each referenced variable;
- **coordinate types**: values required to identify one concrete variable.

Instance-field example:

```text
class Counter { int value; }

VarHandle for Counter.value

variable type:
int

coordinate types:
(Counter)
```

Array element:

```text
int[] array

variable type:
int

coordinate types:
(int[], int)
          ↑ index
```

Thinking in coordinates makes the signatures of `get`, `set`, and `compareAndSet` predictable instead of something to memorize.

</details>

- [Back to top](#back-to-top)

---

## <a id="var-handle-creation">Creating VarHandles for Fields, Array Elements, and byte[]/ByteBuffer Views</a>

<details>
<summary>Click for details</summary>

Instance/static fields:

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();

VarHandle value =
        lookup.findVarHandle(
                Counter.class,
                "value",
                int.class
        );
```

If `Counter` also has `static int globalValue`, static fields use a separate factory:

```java
VarHandle globalValue =
        lookup.findStaticVarHandle(
                Counter.class,
                "globalValue",
                int.class
        );
```

`findVarHandle` is for instance fields; `findStaticVarHandle` is for static fields.

Array elements:

```java
VarHandle element =
        MethodHandles.arrayElementVarHandle(
                int[].class
        );
```

Byte views:

```java
VarHandle ints =
        MethodHandles.byteArrayViewVarHandle(
                int[].class,
                ByteOrder.BIG_ENDIAN
        );
```

`byteBufferViewVarHandle` provides the corresponding abstraction for `ByteBuffer`.

Views introduce byte-order and alignment concerns. Full native-memory/FFM semantics belong to Native Interoperability.

</details>

- [Back to top](#back-to-top)

---

## <a id="var-handle-access-modes">Access-Mode Families</a>

<details>
<summary>Click for details</summary>

VarHandle groups operations by access semantics:

```text
plain
→ get / set

opaque
→ getOpaque / setOpaque

acquire / release
→ getAcquire / setRelease

volatile
→ getVolatile / setVolatile

atomic / update
→ compareAndSet
→ compareAndExchange...
→ getAndSet
→ getAndAdd...
```

A stronger mode is not automatically “better.” It expresses a different ordering/atomicity contract and can carry different costs.

The goal here is to understand that VarHandle lets code **choose an access contract**. Formal memory-model proofs belong to Concurrency.

Not every VarHandle supports every access mode. For example:

- a VarHandle for a `final` field does not support write or atomic-update modes;
- numeric atomic updates are available only for supported numeric variable types;
- bitwise atomic updates are available only for the variable types documented by the API.

Invoking an unsupported access mode throws `UnsupportedOperationException`. Code can query `isAccessModeSupported` before relying on a mode.

</details>

- [Back to top](#back-to-top)

---

## <a id="var-handle-access-mode-type">Access-Mode Types and Signature Polymorphism</a>

<details>
<summary>Click for details</summary>

Like MethodHandle, VarHandle access-mode methods are **signature-polymorphic**.

An access-mode type is derived from:

```text
coordinate types
+
variable type
+
access mode
```

For an instance field `int value`:

```text
coordinates: (Counter)
variable:    int

GET
→ (Counter)int

SET
→ (Counter,int)void

COMPARE_AND_SET
→ (Counter,int,int)boolean
```

The API exposes this directly:

```java
MethodType getType =
        handle.accessModeType(
                VarHandle.AccessMode.GET
        );
```

An incompatible call-site signature can result in `WrongMethodTypeException`.

By default, VarHandle access-mode invocation uses **invoke behavior**: the runtime can apply conversions comparable to `MethodHandle.invoke`/`asType` when the symbolic descriptor is not an exact match but can be adapted to the access-mode type.

```java
VarHandle exact = handle.withInvokeExactBehavior();
```

That view uses **invoke-exact behavior**: the call site must match the `accessModeType` exactly or `WrongMethodTypeException` is thrown. `hasInvokeExactBehavior()` reports the mode, and `withInvokeBehavior()` restores the default invoke-style behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="var-handle-atomic-updates">Atomic Updates and the Compare-and-Set Family</a>

<details>
<summary>Click for details</summary>

The compare-and-set family performs a conditional update:

```java
boolean updated =
        (boolean) VALUE.compareAndSet(
                counter,
                expected,
                next
        );
```

```text
read current
→ witness matches expected under the access mode's comparison semantics?
    ├─ yes → write next + success
    └─ no  → no write + failure
```

For references and most primitives this is close to thinking in terms of `==`. For `float` and `double`, numeric and atomic update modes compare the **bitwise representation**, so NaN payloads and `-0.0` versus `+0.0` can behave differently from primitive `==`.

`compareAndExchange` returns the witness/current value instead of only a boolean. Weak CAS variants may fail spuriously even when the expected value matches, so they are commonly used inside retry loops.

`getAndAdd`, `getAndSet`, and bitwise update variants are read-modify-write operations.

Do not jump from these primitives to “lock-free is always faster.” Correctness, contention, and progress guarantees belong to the Concurrency curriculum.

</details>

- [Back to top](#back-to-top)

---

## <a id="var-handle-memory-model-boundary">Boundary with the Java Memory Model</a>

<details>
<summary>Click for details</summary>

VarHandle defines access modes with different memory-ordering effects, but this module establishes only the API vocabulary and boundary.

```text
plain
opaque
acquire/release
volatile
```

To answer questions such as:

- which operation happens-before another;
- what acquire/release orders;
- whether a CAS loop is linearizable;
- whether an object is safely published;

you need Java Memory Model reasoning from Concurrency.

`VarHandle` also exposes fence operations such as `acquireFence`, `releaseFence`, and `fullFence` (plus related variants). This module only identifies them as ordering primitives; deciding where fences are correct and proving their memory-ordering effects belongs to Concurrency.

```text
Dynamic Runtime
→ VarHandle shape + modes + invocation mechanics

Concurrency
→ correctness implications of memory ordering
```

This boundary avoids teaching an incomplete JMM inside an API chapter.

</details>

- [Back to top](#back-to-top)
