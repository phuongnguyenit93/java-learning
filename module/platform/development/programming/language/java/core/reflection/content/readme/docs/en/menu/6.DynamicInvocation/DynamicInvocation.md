# Acting Dynamically from Metadata

The previous three chapters **discovered** `Field`, `Method`, and `Constructor` descriptors and their metadata. This milestone now uses those selected descriptors to actually **read/write state, invoke behavior, and create objects**. When metadata selects an operation at runtime, the compiler no longer performs the entire type-checking, overload-selection, or argument-shaping job for the caller.

With a direct call:

```java
String result = service.pay(request);
```

the compiler knows `PaymentService`, knows the `pay(PaymentRequest)` signature, and can reject a wrong type immediately. With reflective invocation, code often receives a class, name, or signature from metadata/configuration and must resolve the member before invoking it.

## <a id="field-read-write">Reading and Writing Field Values</a>

After discovery, `get(...)` and `set(...)` operate on a concrete object. Start with a `public` field so the value-access mechanism is observable before access-control rules enter the picture:

~~~java
class Counter {
    public int value = 1;
}

Counter counter = new Counter();
Field publicField = Counter.class.getField("value");

System.out.println(publicField.get(counter)); // 1
publicField.set(counter, 5);
System.out.println(counter.value);            // 5
~~~

Here `publicField` is the descriptor for `value`, while `counter` is the receiver that holds the actual state.

In the running model, `processedCount` is `private`, so discovery can still succeed while value access remains subject to access checks:

~~~java
PaymentService service = new PaymentService("stripe");
Field field = PaymentService.class.getDeclaredField("processedCount");

// These two lines succeed only when reflective access is allowed.
Object value = field.get(service);
field.set(service, 5);
~~~

The split is deliberate: **`getDeclaredField()` finding a member does not mean `get()/set()` is allowed to use it**. Access Control returns to exactly this boundary with `canAccess(...)` and `trySetAccessible()`.

Field.get(...) returns Object. Primitive field values are boxed:

~~~java
int processedCount = (Integer) field.get(service);
~~~

Field also exposes primitive-specific methods such as getInt/setInt and getBoolean/setBoolean:

~~~java
int count = field.getInt(service);
field.setInt(service, 5);
~~~

Static fields belong to the class rather than a particular instance. The receiver argument is ignored for a static field; passing null makes that intention clear:

~~~java
Field channel = PaymentService.class.getField("CHANNEL");
Object value = channel.get(null);
~~~

For an instance field, the receiver must be compatible with the declaring class. A wrong receiver or incompatible value can produce IllegalArgumentException.

Primitive reflective access supports the conversions documented by the Field API, including relevant unboxing/widening cases, but it is not a general conversion system. A String value such as "5" is not automatically parsed into an int.

The useful mental model is:

~~~text
Field descriptor
    +
receiver object (for an instance field)
    +
compatible value (for set)
    ↓
runtime field access
~~~

If the field is private, the existence of the descriptor does not bypass access checks. IllegalAccessException is a normal outcome when the caller does not have reflective access. canAccess(...), trySetAccessible(), and JPMS boundaries are covered in Access Control.

## <a id="method-invoke">Invoking a Method at Runtime</a>

Once a Method has been selected, invoke(receiver, args...) performs the call:

~~~java
PaymentService service = new PaymentService("stripe");
PaymentRequest request = new PaymentRequest("ORD-1", 100_000);

Method method = PaymentService.class.getMethod(
        "pay",
        PaymentRequest.class
);

Object result = method.invoke(service, request);
String paymentId = (String) result;
~~~

Method.invoke returns Object. Primitive return values are boxed; a void method produces null.

For an instance method, the receiver must be compatible with the declaring class. A static method ignores the receiver, and null is the clearest value to pass:

~~~java
Method parse = Long.class.getMethod("parseLong", String.class);
Object value = parse.invoke(null, "100");
~~~

Invocation can perform the unboxing and primitive-widening conversions allowed by the Reflection contract, but it does not redo Java's full compile-time overload selection. Once the descriptor represents a method that accepts long, a compatible boxed primitive may be unboxed/widened; a String such as "100" is not automatically converted to long.

A wrong receiver, wrong argument count, or incompatible argument can produce IllegalArgumentException. A method that is not reflectively accessible from the caller can produce IllegalAccessException. Changing reflective accessibility belongs to Access Control.

Framework code commonly benefits from separating discovery from invocation:

~~~java
Method payMethod = resolvePaymentMethod(PaymentService.class);

// validate/cache once during setup

Object result = payMethod.invoke(service, request);
~~~

That moves configuration errors toward bootstrap time and avoids repeated string-based lookup on every business operation.

## <a id="invocation-exception">InvocationTargetException and the Target Boundary</a>

An important distinction is whether a failure happens **before the target method body runs** or **inside the target method itself**.

Suppose pay(...) rejects an invalid amount:

~~~java
public String pay(PaymentRequest request) throws PaymentException {
    if (request.amount() <= 0) {
        throw new PaymentException("amount must be positive");
    }
    processedCount++;
    return provider + ":" + request.orderId();
}
~~~

A direct call:

~~~java
service.pay(request);
~~~

throws PaymentException according to the method's normal contract.

A reflective call exposes target failure through InvocationTargetException:

~~~java
try {
    method.invoke(service, request);
} catch (InvocationTargetException ex) {
    Throwable targetFailure = ex.getCause();
}
~~~

The wrapper marks a useful boundary:

~~~text
NoSuchMethodException
→ discovery failed

IllegalAccessException
→ reflective access was denied

IllegalArgumentException
→ receiver/arguments did not satisfy the invocation contract

InvocationTargetException
→ the target method was invoked and target code threw
~~~

Frameworks normally inspect or unwrap getCause() and translate it according to their own policy. Logging only the wrapper can hide the business exception that actually explains the failure.

## <a id="varargs-reflection">Varargs and Reflection</a>

Varargs can be confusing because there are **two varargs layers**: the target method may be variable-arity, and Method.invoke(...) itself accepts Object... args.

Consider an additional method used only for this example:

~~~java
public String summarize(String prefix, String... values) {
    return prefix + ":" + String.join(",", values);
}
~~~

Reflection represents the final target parameter as an array:

~~~java
Method method = PaymentService.class.getMethod(
        "summarize",
        String.class,
        String[].class
);

System.out.println(method.isVarArgs()); // true
~~~

The invocation must supply the array as the final reflective argument:

~~~java
Object result = method.invoke(
        service,
        "payments",
        new String[] {"A", "B"}
);
~~~

Reflection does not automatically perform target-level varargs packing in the same way as an ordinary Java call expression. If the target has only one String... parameter:

~~~java
public String join(String... values) { ... }
~~~

make it explicit that String[] is **one reflective argument**:

~~~java
Method join = PaymentService.class.getMethod(
        "join",
        String[].class
);

join.invoke(service, (Object) new String[] {"A", "B"});
~~~

The Object cast prevents the Java compiler from treating String[] as the Object... array belonging to Method.invoke itself and spreading it at the outer call site.

Utilities that invoke methods reflectively should inspect isVarArgs() and normalize arguments according to their own documented rules instead of assuming Method.invoke will reproduce every source-level varargs convenience.

After method invocation, the remaining operation in the basic object lifecycle is construction: **if the concrete class is selected at runtime, how is the original object created?**

## <a id="constructor-newinstance">Creating an Object with Constructor.newInstance</a>

After selecting a constructor descriptor:

~~~java
Constructor<PaymentService> constructor =
        PaymentService.class.getConstructor(String.class);

PaymentService service = constructor.newInstance("stripe");
~~~

This is the reflective counterpart of:

~~~java
PaymentService service = new PaymentService("stripe");
~~~

Constructor varargs have the same boundary already seen with `Method.invoke(...)`: Reflection sees `String...` as `String[]`, while `Constructor.newInstance(...)` itself also accepts `Object...`.

~~~java
class TagBundle {
    TagBundle(String... tags) {}
}

Constructor<TagBundle> tagsConstructor =
        TagBundle.class.getDeclaredConstructor(String[].class);

TagBundle bundle = tagsConstructor.newInstance(
        (Object) new String[] {"fast", "safe"}
);
~~~

The cast to `Object` makes the `String[]` **one constructor argument**. Without it, when the target constructor has only one `String...` parameter, Java can treat the array as the outer `Object...` argument array of `newInstance(...)`, spreading its elements into multiple reflective arguments. Reflection does not perform target-constructor varargs packing the way ordinary constructor-call syntax does.

The key difference is that the constructor can be selected from runtime metadata:

~~~java
static <T> T create(
        Class<T> type,
        Class<?>[] parameterTypes,
        Object[] arguments
) throws ReflectiveOperationException {
    Constructor<T> constructor =
            type.getDeclaredConstructor(parameterTypes);
    return constructor.newInstance(arguments);
}
~~~

A real framework typically validates constructor policy, accessibility, and dependency resolution before the newInstance(...) step.

Arguments may undergo the unboxing/primitive-widening conversions allowed by reflective invocation, but the caller still must supply a valid count and compatible types. Reflection is not a general-purpose parser or domain conversion layer.

Constructor.newInstance(...) should be preferred over the deprecated Class.newInstance(). The older API only targets a no-argument constructor and has a less useful exception contract. A Constructor descriptor represents the exact constructor selected and reports target failures through InvocationTargetException, just like Method.invoke(...).

Reflective construction **still executes the real constructor body**. It does not allocate an object while skipping initialization logic:

~~~java
public PaymentService(String provider) {
    if (provider == null || provider.isBlank()) {
        throw new IllegalArgumentException("provider is required");
    }
    this.provider = provider;
}
~~~

constructor.newInstance("") runs that validation.

## <a id="constructor-reflection-failure">Constructor Reflection Failure Modes</a>

Constructor reflection can fail at several different stages. Separating them lets a framework report the real cause instead of reducing everything to “reflection failed.”

| Failure | Meaning |
| --- | --- |
| NoSuchMethodException | Discovery did not find a constructor with the exact parameter types |
| IllegalAccessException | The constructor exists but reflective access is denied |
| InstantiationException | The declaring class cannot be instantiated through the constructor contract, such as an abstract class |
| IllegalArgumentException | Argument count/type is wrong, or the target is a case Reflection forbids constructing such as an enum |
| InvocationTargetException | The constructor body ran and threw an exception |
| ExceptionInInitializerError | Class initialization was triggered and static initialization failed |

Example: distinguish a protocol failure from a constructor/domain failure:

~~~java
Constructor<PaymentService> constructor =
        PaymentService.class.getConstructor(String.class);

try {
    PaymentService service = constructor.newInstance("");
} catch (InvocationTargetException ex) {
    Throwable constructorFailure = ex.getCause();
    // e.g. IllegalArgumentException("provider is required")
}
~~~

Passing Integer instead of String:

~~~java
constructor.newInstance(123);
~~~

fails the reflective invocation contract rather than the constructor's business validation.

Enums are a special case: enum instances are controlled by the JVM according to the enum declaration, and Constructor.newInstance cannot create additional enum constants.

A private or package-private constructor can still be **discovered** with getDeclaredConstructor(...), but discovery is not permission to invoke it. Whether reflective access can be enabled depends on language/module boundaries and runtime policy. Access Control picks up directly from this point.

At this point the learner has the **three core operations** of this milestone:

~~~text
descriptors have already been discovered
    ↓
Field.get/set
Method.invoke
Constructor.newInstance
    ↓
validate receiver / arguments / conversions / target failures
    ↓
encounter the reflective access boundary
~~~

Before moving to access boundaries, one more synthesis step is useful: model the **reflective dispatch flow** and the checks a dynamic call passes through.

## <a id="reflective-dispatch">Reflective Dispatch Synthesis</a>

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

## <a id="dynamic-array-reflection">Creating and Accessing Arrays When the Component Type Is Known Only at Runtime</a>

Arrays are another form of **metadata-driven operation**. Ordinary Java syntax places the component type directly in source:

```java
String[] values = new String[3];
```

A framework may instead receive a `Class<?> componentType` from configuration, a schema, or a mapping resolved at runtime. `java.lang.reflect.Array` can create the array without hard-coding that component type:

```java
Class<?> componentType = String.class;
Object array = Array.newInstance(componentType, 3);

Array.set(array, 0, "A");
Array.set(array, 1, "B");

Object first = Array.get(array, 0);
System.out.println(first); // A
```

The runtime type of `array` is still `String[]`, even though the compile-time variable in this example is `Object`:

```java
System.out.println(array.getClass());                    // class [Ljava.lang.String;
System.out.println(array.getClass().isArray());          // true
System.out.println(array.getClass().getComponentType()); // class java.lang.String
```

`Array` also supports primitive arrays. Helpers such as `getInt(...)`, `setInt(...)`, and the corresponding primitive-specific methods allow access without first casting to a statically known primitive-array type:

```java
Object numbers = Array.newInstance(int.class, 2);

Array.setInt(numbers, 0, 10);
Array.setInt(numbers, 1, 20);

System.out.println(Array.getInt(numbers, 1)); // 20
```

Multidimensional arrays can be created with the overload that accepts multiple dimensions:

```java
Object matrix = Array.newInstance(String.class, 2, 3);
System.out.println(matrix.getClass()); // class [[Ljava.lang.String;
```

Reflection does not remove array type rules. Invalid indexes still fail, incompatible values are still rejected at runtime, and negative dimensions remain invalid. `Array` only moves the **component type + dimensions + access** decision from compile-time syntax to runtime metadata.

~~~text
component type known at compile time
→ new T[length]

component type known only at runtime
→ Array.newInstance(componentType, length)
→ Array.get / Array.set
~~~

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

Only now is **Acting Dynamically from Metadata** complete: the learner has moved from `get/set`, `invoke`, and `newInstance` through dispatch, argument conversion, dynamic arrays, and the `MethodHandle` boundary. The next ROADMAP milestone is **Access, Encapsulation, and Module Boundaries**: finding a descriptor does not imply permission to use it. Generic signature metadata, Dynamic Proxy, and final synthesis follow afterward.
