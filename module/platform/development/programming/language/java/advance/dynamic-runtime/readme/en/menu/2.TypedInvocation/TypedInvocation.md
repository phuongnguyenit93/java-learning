<a id="back-to-top"></a>

# Typed Invocation Model

## Menu
- [MethodType and Invocation Type Contracts](#method-type-model)
- [MethodHandle as a Typed Executable Reference](#method-handle-model)
- [MethodHandle Signature Polymorphism](#signature-polymorphism)
- [invokeExact and invoke](#invoke-exact-vs-invoke)
- [MethodHandle Type Failures](#invocation-type-failures)

## <a id="method-type-model">MethodType and Invocation Type Contracts</a>

<details>
<summary>Click for details</summary>

`MethodType` is an immutable value object that describes the **return type plus parameter types** of an invocation. It contains no method name, receiver, or implementation.

```java
MethodType type =
        MethodType.methodType(
                String.class,
                String.class
        );
```

```text
(String)String
   │       └── return type
   └────────── parameter types
```

Every MethodHandle has a `type()`. The caller also contributes a symbolic method type at the call site. The JVM uses these contracts to determine whether invocation is exact or whether adaptation is required.

`MethodType` is also the Java-level representation of a JVM method descriptor. For example:

```text
(Ljava/lang/String;)Ljava/lang/String;
```

You do not need constant-pool details here; the important point is that MethodType is the shared call schema used by MethodHandle and invokedynamic.

</details>

- [Back to top](#back-to-top)

---

## <a id="method-handle-model">MethodHandle as a Typed Executable Reference</a>

<details>
<summary>Click for details</summary>

A `MethodHandle` is a **typed, directly executable reference** to a low-level operation such as a method, constructor, field accessor, or composed operation. Think “callable runtime value,” not metadata record.

```java
final class Greeter {
    String greet(String name) {
        return "Hello " + name;
    }
}

MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodType type = MethodType.methodType(String.class, String.class);

MethodHandle greet =
        lookup.findVirtual(
                Greeter.class,
                "greet",
                type
        );
```

`greet.type()` is:

```text
(Greeter, String)String
```

The receiver becomes the **first parameter in the MethodHandle's `MethodType`** for a virtual method. That explains why binding the receiver later produces a new `(String)String` handle.

MethodHandles are immutable. Adapters do not mutate an existing handle; they produce new handles representing new executable pipelines.

</details>

- [Back to top](#back-to-top)

---

## <a id="signature-polymorphism">MethodHandle Signature Polymorphism</a>

<details>
<summary>Click for details</summary>

`MethodHandle.invokeExact` and `invoke` appear in Java source as if they were ordinary methods, but the JVM treats them specially as **signature-polymorphic methods**. The actual descriptor comes from the static types present at the call site.

```java
MethodHandle handle = ...; // (Greeter,String)String

String value =
        (String) handle.invokeExact(
                greeter,
                "Phuong"
        );
```

The `(String)` cast is not merely “casting an Object result.” It helps establish the expected return type of the call-site descriptor.

This matters during debugging: two source lines that look nearly identical can produce different call-site descriptors because an argument or expected return type has a different static type.

</details>

- [Back to top](#back-to-top)

---

## <a id="invoke-exact-vs-invoke">invokeExact and invoke</a>

<details>
<summary>Click for details</summary>

`invokeExact` requires the call-site symbolic type to **exactly match** the MethodHandle type.

```java
MethodHandle h = ...; // (Greeter,String)String

String ok = (String) h.invokeExact(greeter, "A");
```

`invoke` is more flexible: it can apply conversions comparable to `asType` when the conversion is supported.

```java
Object value = h.invoke(greeter, "A");
```

Practical rule:

- prefer `invokeExact` once a pipeline has a normalized exact contract;
- use `invoke` when conversions permitted by `MethodHandle.asType` are intentionally acceptable;
- do not use `invoke` to hide an unclear type design.

Framework code often becomes easier to reason about when handles are normalized to one canonical `MethodType` before exact invocation.

</details>

- [Back to top](#back-to-top)

---

## <a id="invocation-type-failures">MethodHandle Type Failures</a>

<details>
<summary>Click for details</summary>

The characteristic failure is `WrongMethodTypeException`: the caller descriptor and the handle type do not match under the rules of `invokeExact` or `invoke`.

```java
MethodHandle h = ...; // (Greeter,String)String

// Caller expects int, but the handle returns String.
int value = (int) h.invokeExact(greeter, "A");
```

Keep type failures separate from lookup failures:

```text
lookup/find...
→ NoSuchMethodException / NoSuchFieldException
→ IllegalAccessException

invoke/adapt...
→ WrongMethodTypeException
→ ClassCastException on relevant conversion paths
```

Exceptions thrown by the underlying target **propagate unchanged** through `invokeExact`/`invoke`; MethodHandle does not wrap them in `InvocationTargetException` the way `Method.invoke` does. Because the API declares `throws Throwable`, direct callers must catch or declare the appropriate throwable type.

Another edge case is class identity: a `MethodType` contains live `Class` objects. Two classes with the same binary name but loaded by different class loaders are still different types, so MethodHandle type matching accounts for class-loader identity. Deeper class-loading mechanics belong to the ClassLoader module.

Debug checklist:

1. print `handle.type()`;
2. inspect the static type of every argument;
3. inspect the expected return type created by assignment/cast;
4. if adapters are involved, inspect the type before and after each adapter.

Treat MethodType as the schema of an invocation, not incidental metadata.

</details>

- [Back to top](#back-to-top)
