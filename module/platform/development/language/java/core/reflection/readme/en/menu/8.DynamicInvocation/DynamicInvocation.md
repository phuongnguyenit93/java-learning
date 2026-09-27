# Dynamic Invocation

The `Methods` chapter introduced `Method.invoke()`. This chapter focuses on what makes that call different from an ordinary Java call: the compiler is no longer at the call site selecting an overload, validating the target, and preparing arguments. Those decisions move to runtime.

With a direct call:

```java
String result = service.pay(request);
```

the compiler knows `PaymentService`, knows the `pay(PaymentRequest)` signature, and can reject a wrong type immediately. With reflective invocation, code often receives a class, name, or signature from metadata/configuration and must resolve the member before invoking it.

## <a id="reflective-dispatch">Reflective Dispatch Flow</a>

A reflective invocation can be modeled as a sequence:

```text
Class metadata
    ↓ resolve the intended Method
access check
    ↓
validate target + arguments
    ↓
apply permitted runtime conversions
    ↓
dispatch method
    ↓
return value or failure
```

Using the recurring model:

```java
PaymentService service = new PaymentService("stripe");
PaymentRequest request = new PaymentRequest("ORD-42", 150_000L);

Method pay = PaymentService.class.getMethod("pay", PaymentRequest.class);
Object result = pay.invoke(service, request);

System.out.println(result); // stripe:ORD-42
```

`getMethod("pay", PaymentRequest.class)` asks for a specific signature. Reflective lookup does not take arbitrary runtime values and reproduce Java compiler overload resolution for you. If a class has both `pay(String)` and `pay(PaymentRequest)`, dynamic code should resolve the intended signature explicitly.

For an instance method, `Method.invoke()` still uses dynamic method lookup on the runtime target. If the represented method is overridable and the target is an instance of a subclass that overrides it, dispatch can reach the overriding implementation. Reflection changes **how the descriptor and call boundary are selected**; it does not turn virtual dispatch into static dispatch.

Failures can occur at different stages:

- wrong lookup name/signature → `NoSuchMethodException`;
- inaccessible member → `IllegalAccessException`;
- incompatible target, wrong argument count, or invalid conversion → `IllegalArgumentException`;
- `null` target for an instance method → `NullPointerException`;
- the invoked method itself throws → `InvocationTargetException` wrapping the original failure.

Framework code therefore often distinguishes “the dynamic invocation could not be resolved” from “the business method ran and failed.”

```java
try {
    return pay.invoke(service, request);
} catch (InvocationTargetException ex) {
    Throwable targetFailure = ex.getCause();
    // map according to this adapter boundary's contract
    throw new IllegalStateException("Target method failed", targetFailure);
}
```

## <a id="argument-conversion">Argument Conversion during Invocation</a>

`Method.invoke` has the shape `invoke(Object obj, Object... args)`, so arguments pass through an `Object[]`. That does not mean runtime reflection accepts arbitrary values and casts them however necessary.

For primitive formal parameters, reflection can unbox wrappers and perform permitted method-invocation conversions, including primitive widening. It does not automatically perform narrowing conversion.

For example:

```java
final class Counter {
    public void record(long value) {
        System.out.println(value);
    }
}

Method record = Counter.class.getMethod("record", long.class);
Counter counter = new Counter();

record.invoke(counter, Integer.valueOf(7)); // int -> long: valid
record.invoke(counter, Long.valueOf(7));    // long -> long: valid
record.invoke(counter, null);               // fails: null cannot unbox to long
```

If the formal parameter were `int`, passing a `Long` would require narrowing from `long` to `int`; `Method.invoke()` does not perform that conversion and throws `IllegalArgumentException`.

A reference argument must be assignable to the formal reference type. `null` is valid for a reference parameter but not for a primitive parameter. Primitive return values are boxed for the caller of `invoke()`, while a `void` method produces `null`.

Varargs create another boundary that can surprise dynamic code. `Method.invoke()` is itself a varargs API, while the target method can also be varargs. Reflection does not reproduce every source-level compiler packing decision automatically; dynamic code should construct the argument shape expected by the actual target method.

When arguments come from configuration, plugins, or serialized input, validating the resolved signature and values before invocation makes failures easier to diagnose. Otherwise type errors appear only at the `invoke()` boundary.

## <a id="method-handle-boundary">The MethodHandle Boundary</a>

`java.lang.invoke.MethodHandle` is a neighboring dynamic-invocation mechanism, but it operates at a different abstraction level.

For a learner in the Reflection module, it is enough to treat `MethodHandle` as a **neighboring lower-level API for holding and invoking a dynamically resolved callable target**. Understanding Reflection does not require mastering `MethodType`, `Lookup`, or adaptation; the material below only establishes the boundary between the mechanisms.

Core Reflection focuses on **discovering and manipulating metadata**: find a `Method`, inspect annotations, parameters, modifiers, and generic signatures, then optionally invoke it. A `MethodHandle` represents a callable target with a `MethodType` and supports lookup/adaptation for lower-level dynamic linking and invocation scenarios.

A useful mental model is:

```text
Reflection
→ “what member exists and what metadata describes it?”
→ Method.invoke(...) when a descriptor must be called

MethodHandle
→ “what dynamically selected callable target and type shape do I have?”
→ invokeExact/invoke plus explicit adaptation
```

`invokeExact()` requires the call-site type to match the handle's `MethodType` exactly; `invoke()` is more adaptable. Creating handles still uses `MethodHandles.Lookup` access rules, so MethodHandle is not an encapsulation bypass.

Do not replace every `Method.invoke()` simply because MethodHandle is sometimes associated with optimized dynamic invocation. If the main task is inspecting annotations, generic signatures, or members, reflection remains the natural abstraction. If a runtime system resolves a callable target once and repeatedly invokes it through a controlled type contract, MethodHandle may be a better fit. Its full `java.lang.invoke` model is a separate topic rather than an extension of every reflection example.

## <a id="dynamic-invocation-design">Designing a Dynamic Invocation Boundary</a>

Dynamic invocation is justified when the **target genuinely becomes known only at runtime**. Common examples include:

- a framework choosing a handler from annotation or routing metadata;
- a serializer/deserializer selecting an accessor or constructor from a model class;
- a plugin system loading an implementation and invoking a discovered contract;
- testing/tooling that must inspect and call code by runtime name/signature.

If application code already knows it always needs `PaymentService.pay(PaymentRequest)`, a direct call is simpler, type-safe, and easier to refactor.

When dynamic invocation is a real requirement, contain it behind a controlled adapter:

```java
final class PaymentInvoker {
    private final Method payMethod;

    PaymentInvoker(Class<?> serviceType) throws NoSuchMethodException {
        this.payMethod = serviceType.getMethod("pay", PaymentRequest.class);
    }

    String invoke(Object service, PaymentRequest request) {
        try {
            return (String) payMethod.invoke(service, request);
        } catch (InvocationTargetException ex) {
            throw new IllegalStateException("Payment target failed", ex.getCause());
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            throw new IllegalStateException("Payment invocation contract is invalid", ex);
        }
    }
}
```

The string lookup and cast are concentrated in one adapter while the rest of the application can use a typed API. The `Method` is resolved once instead of being looked up on every request. Caching removes repeated metadata lookup, but it does not remove reflection's other trade-offs.

The next chapter brings those trade-offs together: loss of compile-time safety, coupling to internals, runtime overhead, native-image reachability, and refactorability.
