# Constructor Discovery and Construction Metadata

Field and Method describe state and behavior. `Constructor` completes the **Discovering Class Members** milestone by asking: **which constructors does this runtime type declare, and what metadata lets a framework choose the right descriptor?** Actual `Constructor.newInstance(...)` execution belongs to the next **Acting Dynamically from Metadata** milestone.

This is common in dependency-injection containers, plugin systems, serializers/deserializers, and framework bootstrapping. The framework receives a `Class<?>`, discovers constructors, and applies a selection policy before it performs object creation.

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

Varargs constructors are represented with an array as the final parameter and `isVarArgs() == true`. At this point the learner has completed **discovery** for `Field`, `Method`, and `Constructor`; the next chapter uses those descriptors to read/write fields, invoke methods, and create objects at runtime.
