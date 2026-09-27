# Reflecting on Methods

Field reflection lets runtime code work with discovered state. Method reflection does the same for **behavior**: locate a method from metadata, inspect its signature, and invoke it without writing a direct Java call expression for that method in advance.

The running model is:

~~~java
public class PaymentService {
    private final String provider;
    private int processedCount;

    public PaymentService(String provider) {
        this.provider = provider;
    }

    public String pay(PaymentRequest request) throws PaymentException {
        processedCount++;
        return provider + ":" + request.orderId();
    }

    private String internalStatus() {
        return provider + ":" + processedCount;
    }
}
~~~

A framework usually should not begin with Method.invoke(...). It first has to answer three questions: **which method**, **which signature**, and **which receiver/arguments satisfy that signature**. Invocation is the final step.

## <a id="method-discovery">Discovering the Right Method</a>

Methods are selected by name and parameter types:

~~~java
Method pay = PaymentService.class.getMethod(
        "pay",
        PaymentRequest.class
);

Method internalStatus = PaymentService.class.getDeclaredMethod(
        "internalStatus"
);
~~~

getMethod(...) searches the public method surface and may return an inherited method. getDeclaredMethod(...) searches methods declared directly by the current class at any visibility, but does not automatically search inherited methods.

Example:

~~~java
class BaseService {
    public String serviceId() {
        return "base";
    }
}

class PaymentService extends BaseService {
    private String internalStatus() {
        return "ok";
    }
}
~~~

~~~text
PaymentService.class.getMethod("serviceId")
→ finds the inherited public method

PaymentService.class.getDeclaredMethod("serviceId")
→ NoSuchMethodException

PaymentService.class.getDeclaredMethod("internalStatus")
→ finds the private method descriptor
~~~

For overloaded methods, the parameter-type list is required to select the declaration:

~~~java
class PaymentFormatter {
    String format(long amount) { return Long.toString(amount); }
    String format(String amount) { return amount; }
}

Method a = PaymentFormatter.class.getDeclaredMethod("format", long.class);
Method b = PaymentFormatter.class.getDeclaredMethod("format", String.class);
~~~

int.class and Integer.class are different Class objects:

~~~java
PaymentFormatter.class.getDeclaredMethod("format", Long.class);
// NoSuchMethodException unless a Long overload exists
~~~

Discovery requires the **declared parameter types**. It does not perform compiler-style overload resolution using boxing, widening, or a “closest method” search.

For enumeration:

~~~java
Method[] declared = PaymentService.class.getDeclaredMethods();
Method[] publicMethods = PaymentService.class.getMethods();
~~~

Do not depend on array order. The compiler may also emit synthetic or bridge methods, especially around generic overriding. isSynthetic() and isBridge() let framework code recognize them when its policy needs to filter compiler artifacts.

Public instance methods from superinterfaces may appear through `getMethod()`/`getMethods()`, but `static` interface methods are **not inherited** by implementing classes or subinterfaces. To reflect a static interface method, perform the lookup on the interface that actually declares it.

## <a id="method-signature-metadata">Method Signature Metadata</a>

A Method descriptor contains much more than its name:

~~~java
Method method = PaymentService.class.getMethod(
        "pay",
        PaymentRequest.class
);

String name = method.getName();
Class<?> declaringClass = method.getDeclaringClass();
Class<?> returnType = method.getReturnType();
Class<?>[] parameterTypes = method.getParameterTypes();
Class<?>[] exceptionTypes = method.getExceptionTypes();
int modifiers = method.getModifiers();
~~~

`getDeclaringClass()` returns the class or interface that **actually declares that method descriptor**. This can differ from the class where lookup started: `PaymentService.class.getMethod(...)` may return a public method inherited from a superclass or interface, in which case the declaring class is not necessarily `PaymentService`.

A framework can validate a contract before invoking:

~~~java
if (method.getReturnType() != String.class) {
    throw new IllegalStateException("pay must return String");
}

if (!Modifier.isPublic(method.getModifiers())) {
    throw new IllegalStateException("pay must be public");
}
~~~

Generic signatures have parallel APIs:

~~~java
method.getGenericReturnType();
method.getGenericParameterTypes();
method.getGenericExceptionTypes();
~~~

These methods return Type metadata instead of only Class<?> and are covered more deeply in Generic Type Inspection.

Parameter metadata is available through:

~~~java
for (Parameter parameter : method.getParameters()) {
    System.out.println(parameter.getName());
    System.out.println(parameter.getType());
}
~~~

Source-level parameter names are **not always retained by default**. Without build metadata such as the -parameters compiler option, Reflection may expose generated names such as arg0. A framework should not depend on parameter names unless its build/deployment contract guarantees their presence.

Method also exposes useful flags:

~~~java
method.isVarArgs();
method.isDefault();
method.isBridge();
method.isSynthetic();
~~~

isDefault() identifies an interface default method. isBridge()/isSynthetic() warn that a method may be a compiler-generated artifact rather than a source declaration the application author explicitly wrote.

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

The remaining piece of the basic object lifecycle is construction: **if the concrete class is selected at runtime, how is the original object created?**
