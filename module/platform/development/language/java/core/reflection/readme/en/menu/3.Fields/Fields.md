# Reflecting on Fields

Once Class Metadata has identified the runtime type, Reflection can inspect the type's **state** through Field descriptors. This is a core building block for mappers, serializers, validators, and frameworks that need to reason about fields without writing type-specific code for every model.

The running model keeps its baseline members and adds a few field shapes needed for this chapter:

~~~java
public class PaymentService {
    public static final String CHANNEL = "CARD";

    private final String provider;
    private int processedCount;
    private volatile boolean available = true;
    private List<String> supportedCurrencies = List.of("VND", "USD");

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

Field reflection has two separate stages: **discover a Field descriptor**, then **use that descriptor against a particular receiver**. Discovering a private field does not automatically grant permission to read or write it; reflective access rules are handled later in Access Control.

## <a id="field-discovery">Discovering Fields</a>

Class exposes the same public-versus-declared distinction introduced in the previous chapter:

~~~java
Class<PaymentService> type = PaymentService.class;

Field privateField = type.getDeclaredField("processedCount");
Field publicField = type.getField("CHANNEL");
~~~

getDeclaredField(name) searches fields declared directly by the current class, regardless of visibility. getField(name) searches only public fields and may find an inherited public field.

Example:

~~~java
class BaseService {
    public String region = "VN";
}

class PaymentService extends BaseService {
    private int processedCount = 3;
}
~~~

~~~text
PaymentService.class.getDeclaredField("processedCount")
→ found

PaymentService.class.getField("region")
→ finds the inherited public field

PaymentService.class.getDeclaredField("region")
→ NoSuchFieldException

PaymentService.class.getField("processedCount")
→ NoSuchFieldException because the field is not public
~~~

For enumeration:

~~~java
Field[] declared = PaymentService.class.getDeclaredFields();
Field[] publicSurface = PaymentService.class.getFields();
~~~

Do not rely on the returned array order as a semantic contract. A framework that needs deterministic ordering should define its own rule, such as sorting by name or reading explicit metadata.

Reflection may also expose synthetic fields introduced by the compiler. isSynthetic() lets tooling distinguish such artifacts from source-level fields it actually intends to process. A serializer that blindly handles every declared field can accidentally include state that is not part of the model contract.

A more deliberate filter looks like:

~~~java
static List<Field> instanceFields(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
            .filter(field -> !field.isSynthetic())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .toList();
}
~~~

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

## <a id="field-modifiers">Field Modifiers</a>

getModifiers() lets framework code interpret the role of a field:

~~~java
Field field = PaymentService.class.getDeclaredField("available");
int modifiers = field.getModifiers();

boolean isStatic = Modifier.isStatic(modifiers);
boolean isFinal = Modifier.isFinal(modifiers);
boolean isVolatile = Modifier.isVolatile(modifiers);
boolean isTransient = Modifier.isTransient(modifiers);
boolean isPrivate = Modifier.isPrivate(modifiers);
~~~

These flags often drive policy:

~~~text
static
→ class-level state; object mappers often exclude it

final
→ state is designed not to be reassigned after initialization

transient
→ metadata serializers often consider excluding,
  though the final behavior is serializer-specific

volatile
→ the field keeps Java's memory-visibility semantics;
  Reflection only reports/uses the field, it does not redefine those semantics
~~~

Do not treat final as “Reflection can safely mutate it if access is forced open.” Modern Java imposes important restrictions on reflective writes to final fields, and even cases that can be changed may conflict with JVM/compiler assumptions. Framework code should treat final as a design boundary, not as an obstacle to defeat casually.

Field also provides:

~~~java
field.isEnumConstant();
field.isSynthetic();
~~~

Those properties are not simply inferred from the normal Modifier bit mask.

A snapshot tool might define a clear policy:

~~~java
static boolean shouldRead(Field field) {
    int m = field.getModifiers();
    return !Modifier.isStatic(m)
            && !field.isSynthetic();
}
~~~

An explicit policy is safer than “process everything reflection exposes,” because runtime metadata can include members created for compiler/JVM purposes rather than domain modeling.

## <a id="field-type-metadata">Raw and Generic Field Type Metadata</a>

A field can expose two layers of type information:

~~~java
private List<String> supportedCurrencies;
~~~

~~~java
Field field = PaymentService.class
        .getDeclaredField("supportedCurrencies");

Class<?> rawType = field.getType();
Type genericType = field.getGenericType();

System.out.println(rawType);
// interface java.util.List

System.out.println(genericType.getTypeName());
// java.util.List<java.lang.String>
~~~

getType() returns the runtime/raw Class<?>. It is appropriate for questions such as “is this field assignable to List?”.

getGenericType() returns java.lang.reflect.Type and can preserve declaration-signature metadata. For List<String>, it will commonly be a ParameterizedType rather than only the raw List Class.

~~~java
if (genericType instanceof ParameterizedType parameterized) {
    Type elementType = parameterized.getActualTypeArguments()[0];
    System.out.println(elementType.getTypeName());
}
~~~

With a type variable:

~~~java
class Box<T> {
    T value;
}
~~~

getType() reflects the erased runtime field type (typically Object when T has no narrower bound), while getGenericType() can return a TypeVariable representing T.

This does not mean every object carries all of its generic type arguments at runtime. Java still uses erasure for execution; Reflection is reading **generic signature metadata retained on declarations**. ParameterizedType, TypeVariable, WildcardType, and GenericArrayType are covered in depth by Generic Type Inspection.

Field reflection therefore connects three layers:

~~~text
Class Metadata
→ overall type/member structure

Field
→ state descriptor + runtime value access

Generic Type Inspection
→ richer declaration signatures when Class<?> is not enough
~~~

With state covered, the next step is behavior: **how does runtime code find the right Method, understand its signature, and invoke it safely?**
