# Reflecting on Constructors

Method reflection works with behavior on an object that already exists. Constructor reflection solves the earlier problem: **how can code create an object when the concrete class and constructor are selected only at runtime?**

This is common in dependency-injection containers, plugin systems, serializers/deserializers, and framework bootstrapping. The framework receives a Class<?> and applies a constructor-selection policy instead of writing new PaymentService(...) directly in its own source.

The baseline model remains:

~~~java
public class PaymentService {
    private final String provider;
    private int processedCount;

    public PaymentService(String provider) {
        this.provider = provider;
    }

    public String pay(PaymentRequest request) {
        processedCount++;
        return provider + ":" + request.orderId();
    }

    private String internalStatus() {
        return provider + ":" + processedCount;
    }
}
~~~

This chapter adds one overload only to demonstrate constructor selection:

~~~java
public PaymentService(String provider, int initialProcessedCount) {
    this.provider = provider;
    this.processedCount = initialProcessedCount;
}
~~~

## <a id="constructor-discovery">Discovering Constructors</a>

Constructors are not inherited, so their hierarchy rules are simpler than methods, but Java still exposes public and declared views:

~~~java
Constructor<PaymentService> publicCtor =
        PaymentService.class.getConstructor(String.class);

Constructor<PaymentService> declaredCtor =
        PaymentService.class.getDeclaredConstructor(
                String.class,
                int.class
        );
~~~

getConstructor(...) finds a public constructor declared by the class. getDeclaredConstructor(...) finds a constructor declared by the class at any visibility.

The parameter types must match the declaration exactly during discovery:

~~~java
PaymentService.class.getDeclaredConstructor(
        String.class,
        Integer.class
);
// NoSuchMethodException if the real constructor accepts int
~~~

Like Method discovery, constructor lookup does not run compiler-style overload resolution and does not treat Integer.class as equivalent to int.class.

For enumeration:

~~~java
Constructor<?>[] publicCtors =
        PaymentService.class.getConstructors();

Constructor<?>[] allDeclaredCtors =
        PaymentService.class.getDeclaredConstructors();
~~~

Frameworks should not pick “the first constructor” as an implicit policy. Reflection array order is not a source-level semantic contract. A DI container should define an explicit rule such as an annotation, a single available constructor, or another documented selection policy.

A frequent mistake is assuming every class has a no-argument constructor:

~~~java
PaymentService.class.getDeclaredConstructor();
// NoSuchMethodException for the baseline PaymentService
~~~

Java supplies a default no-argument constructor only when the source declares no constructor at all. Once PaymentService(String provider) exists, the compiler does not silently add PaymentService().

Non-static inner classes provide another edge case: the runtime constructor signature normally includes a parameter for the enclosing instance. The reflective signature can therefore contain a parameter that is less obvious from ordinary source-level construction syntax.

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

## <a id="constructor-metadata">Constructor Metadata</a>

Constructor<?> exposes metadata similar to Method, but a constructor has no return type:

~~~java
Constructor<PaymentService> constructor =
        PaymentService.class.getConstructor(String.class);

Class<PaymentService> owner = constructor.getDeclaringClass();
Class<?>[] parameterTypes = constructor.getParameterTypes();
Type[] genericParameterTypes = constructor.getGenericParameterTypes();
Class<?>[] exceptionTypes = constructor.getExceptionTypes();
int modifiers = constructor.getModifiers();
boolean varArgs = constructor.isVarArgs();
boolean synthetic = constructor.isSynthetic();
~~~

Parameter metadata is also available:

~~~java
for (Parameter parameter : constructor.getParameters()) {
    System.out.println(parameter.getName());
    System.out.println(parameter.getType());
}
~~~

As with method parameters, source-level names are only dependable when the build retains the relevant parameter-name metadata. Otherwise Reflection may expose generated names such as arg0 and arg1.

Framework code can build constructor-selection rules from metadata:

~~~java
static boolean acceptsProvider(Constructor<?> constructor) {
    return Arrays.equals(
            constructor.getParameterTypes(),
            new Class<?>[] {String.class}
    );
}
~~~

Constructors may also declare checked exceptions:

~~~java
public PaymentService(String provider)
        throws ConfigurationException {
    // ...
}
~~~

getExceptionTypes() exposes that declaration. If construction actually throws the exception, Constructor.newInstance(...) exposes it as the cause of InvocationTargetException.

Varargs constructors are represented with an array as the final parameter and isVarArgs() == true. The same outer Object.../array caveats discussed for Method invocation apply.

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

After the first five chapters, the basic reflection flow is complete:

~~~text
obtain Class<?> at runtime
    ↓
inspect type metadata
    ↓
discover Field / Method / Constructor
    ↓
read state, invoke behavior, or create an object
    ↓
encounter access, generic-metadata, and dynamic-invocation boundaries
~~~

The next chapter addresses the most common misconception about Reflection: **does finding a private member mean Reflection is automatically allowed to use it?**
