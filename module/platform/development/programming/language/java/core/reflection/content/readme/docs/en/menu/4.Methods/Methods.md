# Method Discovery and Signature Metadata

After `Field`, the **Discovering Class Members** milestone moves to **behavior**: locate a `Method` from metadata and inspect its signature without requiring a specific method call to be hard-coded in source. Actual `Method.invoke(...)` execution belongs to the later **Acting Dynamically from Metadata** milestone.

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

These methods return `Type` metadata instead of only `Class<?>` and are covered more deeply in **Generic Signature Metadata after Erasure**.

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

After Field and Method discovery, the remaining member kind is **Constructor**: which constructors exist, what parameter/exception/modifier metadata describes them, and which descriptor should a framework choose before object creation?
