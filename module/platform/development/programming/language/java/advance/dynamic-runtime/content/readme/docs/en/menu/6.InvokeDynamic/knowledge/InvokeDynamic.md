<a id="back-to-top"></a>

# invokedynamic and Bootstrap Linkage

## Menu
- [What Problem Does invokedynamic Solve?](#invokedynamic-purpose)
- [Bootstrap Method Contract](#bootstrap-method-contract)
- [Lookup, Name, MethodType, and Static Arguments](#bootstrap-linkage-inputs)
- [invokedynamic Linkage Lifecycle](#invokedynamic-linkage-lifecycle)
- [CallSite Validation and BootstrapMethodError](#invokedynamic-linkage-failures)
- [Lambdas, String Concatenation, and Runtime Consumers](#invokedynamic-runtime-consumers)

## <a id="invokedynamic-purpose">What Problem Does invokedynamic Solve?</a>

<details>
<summary>Click for details</summary>

Traditional JVM invocation instructions such as `invokevirtual` and `invokestatic` describe a relatively direct symbolic target. `invokedynamic` exposes a different extension point: **bootstrap logic decides how the call site is linked**.

```text
invokedynamic
→ JVM invokes a bootstrap method when the call site must be linked
→ bootstrap returns a CallSite
→ subsequent calls dispatch through that CallSite
```

This lets language runtimes and JDK runtime libraries implement custom invocation semantics without requiring a new JVM instruction for every language feature.

Ordinary Java source rarely emits invokedynamic directly. The compiler/runtime may use it for features such as lambdas or modern string concatenation. This chapter teaches the runtime mechanism, not class-file generation.

</details>

- [Back to top](#back-to-top)

---

## <a id="bootstrap-method-contract">Bootstrap Method Contract</a>

<details>
<summary>Click for details</summary>

A bootstrap method is invoked by the JVM to link a dynamic call site. A common shape is:

```java
static CallSite bootstrap(
        MethodHandles.Lookup lookup,
        String name,
        MethodType type
) throws Throwable
```

Additional static arguments may also be passed from the class file.

The bootstrap typically:

1. inspects the context (`Lookup`, symbolic name, `MethodType`, static args);
2. resolves or creates a target MethodHandle;
3. adapts the target to the exact call-site type if required;
4. returns a CallSite.

```java
MethodHandle target = ...;
target = target.asType(type);
return new ConstantCallSite(target);
```

Bootstrap code should perform **linkage work**, not business logic that is meant to execute on every invocation.

</details>

- [Back to top](#back-to-top)

---

## <a id="bootstrap-linkage-inputs">Lookup, Name, MethodType, and Static Arguments</a>

<details>
<summary>Click for details</summary>

The core inputs are:

- `Lookup`: access context of the class containing the dynamic call site;
- `name`: symbolic operation name supplied by the class file/caller;
- `MethodType`: descriptor of the dynamic call site.

Static bootstrap arguments carry additional configuration encoded in the class file. They are **loadable constant-pool values** such as primitive/String constants, `Class`, `MethodHandle`, or `MethodType`; some values may themselves require dynamic resolution before bootstrap receives them.

```text
caller class
   ↓ Lookup

symbolic operation
   ↓ name

call contract
   ↓ MethodType

loadable constant-pool values
   ↓ static args

bootstrap policy
   ↓
CallSite
```

Lookup matters because bootstrap code often needs to resolve members using the caller's access context, not arbitrary bootstrap implementation privileges.

</details>

- [Back to top](#back-to-top)

---

## <a id="invokedynamic-linkage-lifecycle">invokedynamic Linkage Lifecycle</a>

<details>
<summary>Click for details</summary>

Simplified lifecycle:

```text
1. JVM encounters an unlinked invokedynamic instruction
        ↓
2. Resolve bootstrap method + static bootstrap arguments
        ↓
3. Invoke bootstrap
        ↓
4. Bootstrap returns a CallSite
        ↓
5. JVM validates the CallSite type
        ↓
6. Instruction becomes linked to that CallSite
        ↓
7. Invocation dispatches through the current target
```

Bootstrap can be executed lazily when the instruction first needs linking. Therefore “bootstrap runs on every call” is incorrect.

If several threads reach the same still-unlinked call site concurrently, the bootstrap method **may be invoked concurrently more than once**. The JVM installs one result; other bootstrap invocations may finish but their results are ignored. Bootstrap logic that touches shared application state must therefore still be thread-safe.

If a mutable CallSite later changes target, the instruction remains linked to the same CallSite; only the target observed through the site changes. Each `invokedynamic` instruction transitions from unlinked to linked at most once, although bootstrap logic may intentionally return the same `CallSite` object for multiple different instructions.

</details>

- [Back to top](#back-to-top)

---

## <a id="invokedynamic-linkage-failures">CallSite Validation and BootstrapMethodError</a>

<details>
<summary>Click for details</summary>

The CallSite returned by bootstrap must match the invokedynamic instruction's descriptor.

Failure semantics need one important distinction:

- if bootstrap throws an `Error` or subclass of `Error`, that error itself becomes the resolution failure;
- if bootstrap throws a non-`Error` `Throwable`, the JVM wraps it in `BootstrapMethodError` and preserves the original throwable as the cause;
- if bootstrap returns `null`, returns a non-`CallSite` object, or returns a `CallSite` whose type does not match the instruction descriptor, resolution fails with `BootstrapMethodError`.

For the same `invokedynamic` instruction, once resolution has failed, later executions observe the same resolution error and the bootstrap is not re-executed for that instruction.

Useful debugging flow:

```text
invokedynamic failure
→ what is the call-site MethodType?
→ what name/type reached bootstrap?
→ what is target.type() before adaptation?
→ what is the returned CallSite.type()?
→ if this is BootstrapMethodError, what is its original cause?
→ if this is another Error, which error escaped bootstrap/resolution directly?
```

`BootstrapMethodError` is normally a linkage/programming/configuration problem, not a business exception to recover from.

</details>

- [Back to top](#back-to-top)

---

## <a id="invokedynamic-runtime-consumers">Lambdas, String Concatenation, and Runtime Consumers</a>

<details>
<summary>Click for details</summary>

Two familiar consumers:

- lambdas can be linked through `LambdaMetafactory`;
- string concatenation can use `StringConcatFactory`.

```java
Function<String, String> upper =
        value -> value.toUpperCase();
```

Do not conclude that “a lambda is invokedynamic.” Lambda is a **language feature**; invokedynamic is one runtime/linkage mechanism the compiler and JDK can use to implement that feature.

Likewise:

```java
String result = "Hello " + name;
```

does not mean business code is deliberately programming against Dynamic Runtime APIs.

Keeping consumer and mechanism separate prevents this chapter from becoming a history of every feature implemented with invokedynamic.

</details>

- [Back to top](#back-to-top)
