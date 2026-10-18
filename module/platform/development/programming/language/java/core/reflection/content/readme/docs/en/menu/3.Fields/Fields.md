# Field Discovery and State Metadata

Once **Runtime Type Model with Class<?>** has identified the runtime type, Reflection can discover the type's declared **state** through `Field` descriptors. This chapter focuses on finding fields and inspecting descriptor metadata; `get/set` operations belong to the later **Acting Dynamically from Metadata** milestone.

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

Field reflection has two separate stages: **discover a Field descriptor**, then **use that descriptor against a particular receiver**. This chapter focuses only on discovery; value access belongs to **Acting Dynamically from Metadata**, while access boundaries are handled afterward by **Access, Encapsulation, and Module Boundaries**.

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
→ the field value/reference may be assigned only according to final-field rules;
  if it refers to a mutable object, that object's internal state can still change

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

This does not mean every object carries all of its generic type arguments at runtime. Java still uses erasure for execution; Reflection is reading **generic signature metadata retained on declarations**. `ParameterizedType`, `TypeVariable`, `WildcardType`, and `GenericArrayType` are covered in depth by **Generic Signature Metadata after Erasure**.

Field reflection therefore connects three metadata layers:

~~~text
Runtime Type Model
→ overall type/member structure

Field
→ state descriptor + type/modifier/signature metadata

Generic Signature Metadata after Erasure
→ richer declaration signatures when Class<?> is not enough
~~~

With field discovery covered, the next part of **Discovering Class Members** is behavior metadata: **how does runtime code find the right `Method` and understand its signature before invoking it?**
