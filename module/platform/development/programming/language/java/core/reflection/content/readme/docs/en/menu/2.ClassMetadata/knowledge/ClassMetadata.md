# Runtime Type Model with Class<?>

The previous chapter established Class<?> as Reflection's main entry point. The next question a framework asks is: **what kind of runtime type is this, and what does its overall structure look like?**

Class-level metadata does not yet read object state or invoke behavior. It lets code classify a type, choose an appropriate form of its name, inspect modifiers, understand its inheritance relationships, and decide which member-discovery rules to use next.

The running model is:

~~~java
public interface PaymentProcessor {
    String pay(PaymentRequest request);
}

public class PaymentService implements PaymentProcessor {
    private final String provider;
    private int processedCount;

    public PaymentService(String provider) {
        this.provider = provider;
    }

    @Override
    public String pay(PaymentRequest request) {
        processedCount++;
        return provider + ":" + request.orderId();
    }

    private String internalStatus() {
        return provider + ":" + processedCount;
    }
}
~~~

## <a id="class-object-entrypoints">Class<?> as the Reflection Entry Point</a>

Reflection normally begins with a Class<?> object. A Class object is not the source code of a class; it is the runtime object that represents a type known to the JVM.

Three common entry points are:

~~~java
// 1. The type is known directly in source
Class<PaymentService> a = PaymentService.class;

// 2. An object is available, but its concrete type may not be known
Object service = new PaymentService("stripe");
Class<?> b = service.getClass();

// 3. Only the binary class name is known at runtime
Class<?> c = Class.forName("com.example.PaymentService");
~~~

A class literal fits code that already references the type. Object.getClass() is common in framework code that receives an arbitrary object and wants its concrete runtime class. Class.forName(...) is useful for plugin/configuration scenarios where the class name arrives at runtime.

`Class<T>` also carries compile-time type information. `Class<PaymentService>` says that this `Class` object represents `PaymentService`. `Class<?>` means "this is a valid `Class` object, but the concrete represented type is not known here." Framework code commonly works with `Class<?>` precisely because discovering the concrete type is part of the runtime problem; it then validates metadata before casting or operating on values.

Primitive and array types also have Class objects:

~~~java
Class<Integer> primitive = int.class;
Class<String[]> array = String[].class;
~~~

`Class.forName(String)` **initializes the class by default** after locating/loading it, so static initialization may run. The overload `Class.forName(name, false, loader)` can request that initialization not happen at that step. The detailed lifecycle and class-identity rules belong to the ClassLoader module; the important point here is that resolving a class by name can have different side effects from simply using a class literal.

Once a Class<?> exists, reflection can move into more specific questions:

~~~java
Class<?> type = PaymentService.class;

String name = type.getName();
Field[] fields = type.getDeclaredFields();
Method[] methods = type.getDeclaredMethods();
Constructor<?>[] constructors = type.getDeclaredConstructors();
~~~

The rest of **Runtime Type Model with Class<?>** explores what the runtime knows about the type itself. The Field, Method, and Constructor chapters then focus on each member kind.

## <a id="class-names">Class Names</a>

Class exposes several naming APIs because “the name of a type” serves different purposes.

~~~java
Class<?> type = PaymentService.class;

System.out.println(type.getName());
System.out.println(type.getSimpleName());
System.out.println(type.getCanonicalName());
System.out.println(type.getTypeName());
~~~

For an ordinary top-level class, the output is straightforward:

~~~text
getName()          → com.example.PaymentService
getSimpleName()    → PaymentService
getCanonicalName() → com.example.PaymentService
getTypeName()      → com.example.PaymentService
~~~

These methods are not interchangeable.

getName() returns the **binary name** for an ordinary class or interface. Nested classes therefore use the $ form:

~~~java
class PaymentModule {
    static class Config {}
}

System.out.println(PaymentModule.Config.class.getName());
// com.example.PaymentModule$Config
~~~

getSimpleName() returns a short display-oriented name, but an anonymous class may return an empty string. It should not be treated as a globally stable identifier for persistence, serialization, or registries.

getCanonicalName() returns a canonical name when the type has one. Local and anonymous classes may return null, so framework code must not assume that every Class maps cleanly to a source-style canonical name.

getTypeName() is designed for readable type descriptions. Arrays show why it can be preferable to getName():

~~~java
System.out.println(String[].class.getName());      // [Ljava.lang.String;
System.out.println(String[].class.getTypeName());  // java.lang.String[]
~~~

Primitive types also have names:

~~~java
System.out.println(int.class.getName());      // int
System.out.println(int.class.getTypeName());  // int
~~~

Choose the naming API according to the contract: binary names for lookups such as Class.forName(...), simple names for display, and canonical/type names for readable descriptions when applicable.

## <a id="class-kind">Classifying the Runtime Type</a>

A Class<?> can represent several different kinds of Java type. Framework rules often need to classify the type before doing anything else.

~~~java
Class<?> type = PaymentService.class;

boolean interfaceType = type.isInterface();
boolean enumType = type.isEnum();
boolean recordType = type.isRecord();
boolean annotationType = type.isAnnotation();
boolean arrayType = type.isArray();
boolean primitiveType = type.isPrimitive();
boolean syntheticType = type.isSynthetic();
~~~

Examples:

~~~java
record PaymentRequest(String orderId, long amount) {}
enum PaymentStatus { CREATED, PAID }

System.out.println(PaymentProcessor.class.isInterface()); // true
System.out.println(PaymentRequest.class.isRecord());      // true
System.out.println(PaymentStatus.class.isEnum());         // true
System.out.println(String[].class.isArray());              // true
System.out.println(long.class.isPrimitive());              // true
~~~

These distinctions matter because different kinds of type obey different rules. A serializer may handle records through record components. A proxy mechanism may require an interface. An object factory cannot instantiate an abstract class as if it were concrete. Arrays have Class objects even though there is no ordinary source-level class declaration for each array type.

The categories are not always mutually exclusive. An annotation type is also an interface in the Java type system, so framework code should test the property it actually cares about instead of assuming the isXxx() methods create a single exclusive taxonomy.

isSynthetic() identifies declarations generated and marked synthetic by the compiler. Reflection can expose artifacts that are not obvious in source, so tools often need an explicit policy for including or filtering them.

Beyond asking "what kind of type is this?", framework code often needs to validate **runtime type relationships** before casting or registering an implementation. `Class` provides checked APIs that are safer than blind casts:

~~~java
Class<?> discovered = PaymentService.class;
Object service = new PaymentService("stripe");

System.out.println(discovered.isInstance(service));
// true — the dynamic equivalent of an instanceof-style check

System.out.println(
        PaymentProcessor.class.isAssignableFrom(discovered)
);
// true — a PaymentService value can be assigned to PaymentProcessor

PaymentService typed = PaymentService.class.cast(service);

Class<? extends PaymentProcessor> processorType =
        discovered.asSubclass(PaymentProcessor.class);
~~~

The direction of `isAssignableFrom(...)` is easy to reverse accidentally. Read `A.isAssignableFrom(B)` as: **can a value of B be assigned to a variable of type A?** `cast(...)` performs a checked runtime cast; `asSubclass(...)` performs the corresponding check at the `Class`-object level and throws `ClassCastException` when the discovered type is not an appropriate subtype.

## <a id="specialized-type-metadata">Specialized Metadata for Arrays, Enums, Records, and Sealed Types</a>

Not every `Class<?>` has the same meaningful metadata. After identifying a type's **kind**, reflection exposes APIs specialized for that kind.

For arrays, `Class` exposes the immediate component type:

~~~java
Class<?> arrayType = String[][].class;

System.out.println(arrayType.isArray());
System.out.println(arrayType.getComponentType());
System.out.println(arrayType.getComponentType().getComponentType());
~~~

`getComponentType()` returns `null` for a non-array type. A multidimensional array is peeled one dimension at a time because the component type of `String[][]` is `String[]`.

For enums, `getEnumConstants()` returns constants in declaration order:

~~~java
enum PaymentStatus { CREATED, PAID, FAILED }

PaymentStatus[] values = PaymentStatus.class.getEnumConstants();
~~~

For a non-enum type, `getEnumConstants()` returns `null`.

Records have dedicated metadata because a **record component** is a language-level concept, not merely another name for a field or method:

~~~java
record PaymentRequest(String orderId, long amount) {}

for (RecordComponent component : PaymentRequest.class.getRecordComponents()) {
    System.out.println(component.getName());
    System.out.println(component.getType());
    System.out.println(component.getAccessor());
}
~~~

`getRecordComponents()` returns components in record-header order and returns `null` when the type is not a record. `RecordComponent` also exposes generic-type and annotation metadata, allowing serializers and frameworks to reason from the **record contract** rather than reconstructing it indirectly from private fields or accessors.

Sealed classes and interfaces also expose their declared restriction:

~~~java
sealed interface Payment permits CardPayment, BankTransfer {}
final class CardPayment implements Payment {}
final class BankTransfer implements Payment {}

Class<?> paymentType = Payment.class;

if (paymentType.isSealed()) {
    for (Class<?> permitted : paymentType.getPermittedSubclasses()) {
        System.out.println(permitted.getName());
    }
}
~~~

`getPermittedSubclasses()` exposes direct permitted subtypes for a sealed type and returns `null` for a non-sealed type. Reflection observes the declared contract; designing sealed hierarchies remains a type/OOP concern.

~~~text
general Class<?>
    ↓ classify kind
    ├─ array  → component type
    ├─ enum   → enum constants
    ├─ record → record components
    └─ sealed → permitted direct subtypes
~~~

## <a id="runtime-annotation-metadata">Annotations as Runtime Metadata</a>

One major reflection use case is consuming annotations retained with `RUNTIME`. Reflection does not decide annotation lifetime; retention, target, repeatability, and inheritance semantics belong to the Annotation module. Here the annotation is viewed from the **runtime consumer** side.

`Class`, `Field`, `Method`, `Constructor`, `Parameter`, `RecordComponent`, and many other reflective elements participate in the `AnnotatedElement` contract, giving them a largely shared lookup vocabulary. In the example below, assume `@Audit` is a custom annotation declared with `RUNTIME` retention in the Annotation module:

~~~java
Deprecated deprecated = PaymentService.class
        .getAnnotation(Deprecated.class);

Annotation[] declared = PaymentService.class
        .getDeclaredAnnotations();

Audit[] audits = PaymentService.class
        .getAnnotationsByType(Audit.class);
~~~

These calls answer different questions:

- `getAnnotation(...)` follows the `AnnotatedElement` "present" lookup semantics;
- `getDeclaredAnnotation(...)` / `getDeclaredAnnotations()` inspect only the current declaration;
- `getAnnotationsByType(...)` is appropriate when an annotation can repeat and container semantics matter.

Reflection sees only annotations that survive to runtime. A `SOURCE`- or `CLASS`-retained annotation does not become visible through these runtime APIs merely because it existed in source or the class file.

~~~text
Annotation module
→ define the metadata contract and retention

Reflection module
→ consume RUNTIME metadata and choose runtime behavior
~~~

## <a id="modifiers">Inspecting Class Modifiers</a>

getModifiers() returns a bit mask. java.lang.reflect.Modifier provides helpers for interpreting the relevant bits.

~~~java
int modifiers = PaymentService.class.getModifiers();

System.out.println(Modifier.isPublic(modifiers));
System.out.println(Modifier.isAbstract(modifiers));
System.out.println(Modifier.isFinal(modifiers));
System.out.println(Modifier.toString(modifiers));
~~~

An object factory can use modifier metadata to reject obviously non-instantiable classes before constructor discovery:

~~~java
static boolean canConstruct(Class<?> type) {
    int modifiers = type.getModifiers();
    return !type.isInterface()
            && !Modifier.isAbstract(modifiers)
            && !type.isEnum()
            && !type.isPrimitive()
            && !type.isArray();
}
~~~

This is still only an early filter. Passing these checks does not guarantee that the framework can construct the type: a suitable constructor, reflective access, compatible arguments, and constructor-body failures still have to be handled during Constructor Reflection.

Modifier bits must be interpreted in the context of the element being inspected. The Modifier API is shared by classes, fields, methods, and constructors, but not every modifier is meaningful for every kind of element.

There is no dedicated “package-private” bit. If public, protected, and private are all false, the source-level access may be package-private, but actual reflective accessibility still depends on caller context. Access Control handles that later.

Properties such as synthetic, annotation, enum, or record also have dedicated APIs because they are not simply ordinary source modifiers.

Java 21 also exposes `accessFlags()`, which returns `Set<AccessFlag>` so class-file/JVM access and property flags can be inspected as enum values rather than only as a bit mask:

~~~java
Set<AccessFlag> flags = PaymentService.class.accessFlags();
System.out.println(flags.contains(AccessFlag.PUBLIC));
System.out.println(flags.contains(AccessFlag.FINAL));
~~~

`Modifier` remains the familiar core API for Java modifiers. `AccessFlag` is a newer model for flags represented in JVM/class-file metadata; the two APIs are related but are not merely different spellings of the same abstraction. `Field`, `Method`, and `Constructor`/`Executable` also expose `accessFlags()` when tooling needs this lower-level flag model.

## <a id="superclass-interfaces">Superclass and Interface Metadata</a>

Reflection can move from a type to its direct inheritance relationships:

~~~java
Class<?> type = PaymentService.class;

Class<?> parent = type.getSuperclass();
Class<?>[] interfaces = type.getInterfaces();
~~~

For the running PaymentService:

~~~text
getSuperclass() → java.lang.Object
getInterfaces() → [PaymentProcessor]
~~~

getInterfaces() returns the interfaces a class directly implements, or the superinterfaces an interface directly extends. It does not flatten the entire interface graph. A framework that needs the full closure must traverse further and avoid revisiting the same interfaces.

Important getSuperclass() boundaries include:

| Type | getSuperclass() |
| --- | --- |
| ordinary class | direct superclass |
| Object | null |
| interface | null |
| primitive | null |
| void | null |
| array | Object |

Array classes also report Cloneable and Serializable through getInterfaces(), even though programmers never write a normal class declaration for an array type.

A simple superclass traversal looks like:

~~~java
static List<Class<?>> classHierarchy(Class<?> type) {
    List<Class<?>> result = new ArrayList<>();
    for (Class<?> current = type;
         current != null;
         current = current.getSuperclass()) {
        result.add(current);
    }
    return result;
}
~~~

When generic superclass/interface signatures matter, `Class` also provides `getGenericSuperclass()` and `getGenericInterfaces()`. The full `Type` model belongs to **Generic Signature Metadata after Erasure**; `Class<?>` alone is not the entire generic metadata story.

## <a id="member-abstraction-model">Shared Member Descriptor Model</a>

Before separating `Field`, `Method`, and `Constructor`, it helps to see that they are not three unrelated API families. Core Reflection exposes shared abstractions that let framework code reason about members at a higher level:

| Abstraction | Role |
| --- | --- |
| `Member` | Common contract for `Field`, `Method`, and `Constructor`: name, declaring class, modifiers, synthetic state |
| `Executable` | Shared base of `Method` and `Constructor`: parameters, exceptions, varargs, generic declarations |
| `AccessibleObject` | Shared base for `Field`, `Method`, and `Constructor` reflective access checks |
| `AnnotatedElement` | Runtime annotation lookup contract implemented by `AccessibleObject` and many other program elements |

~~~text
                 Member
             ┌─────┴──────┐
           Field       Executable
                         ├─ Method
                         └─ Constructor

Field ───────────────┐
Executable ──────────┴─→ AccessibleObject → AnnotatedElement
~~~

This is a learning-oriented view rather than the entire `java.lang.reflect` hierarchy. For example, `Executable` also implements `GenericDeclaration`; `Class<?>` implements `AnnotatedElement` but is not a `Member`.

A practical flow is:

~~~text
discovery
→ obtain a descriptor
→ inspect shared metadata
→ check access
→ perform the Field / Method / Constructor-specific operation
~~~

The next three chapters can therefore focus on behavior specific to each member kind without relearning modifiers, annotations, or the access abstraction from scratch.

## <a id="declared-vs-public-members">Declared Members versus the Public Surface</a>

This distinction is the most important preparation for the Field, Method, and Constructor chapters.

The two API families answer different questions:

~~~text
getDeclaredXxx(...)
→ members declared directly by this class
→ includes public/protected/package-private/private
→ does not automatically include inherited members

getXxx(...)
→ the public view
→ for fields/methods, may include inherited public members
~~~

Example:

~~~java
class BaseService {
    public String serviceName() {
        return "base";
    }
}

class PaymentService extends BaseService {
    private int processedCount;

    private String internalStatus() { return "ok"; }
    public String pay(PaymentRequest request) { return "ok"; }
}
~~~

Conceptually:

~~~text
PaymentService.class.getDeclaredMethod("internalStatus")
→ finds the private method declared by PaymentService

PaymentService.class.getMethod("serviceName")
→ finds the inherited public method from BaseService

PaymentService.class.getDeclaredMethod("serviceName")
→ NoSuchMethodException
~~~

For fields:

~~~text
getDeclaredFields() → fields declared directly, any visibility
getFields()         → public fields on the class + inherited public fields
~~~

For methods:

~~~text
getDeclaredMethods() → methods declared directly, any visibility
getMethods()         → public methods exposed through class/interface hierarchy
~~~

One important exception: `static` interface methods are **not inherited** by implementing classes or subinterfaces. Do not read "public methods through the interface hierarchy" as meaning that every static helper declared on an interface becomes an inherited method of the implementing class; a static interface method belongs to the interface that declares it.

Constructors are different because constructors are **never inherited**:

~~~text
getDeclaredConstructors() → all constructors declared by the class
getConstructors()         → public constructors declared by the class
~~~

Array-returning discovery APIs do not provide a source-order contract that framework logic should depend on. Do not select “the first method” or “the first constructor” as a semantic rule. Select members by explicit criteria such as name, parameter types, annotations, modifiers, or a documented framework policy.

With the type-level map established, the next question becomes state-oriented: **which fields exist, and what metadata describes them?** Reading and writing their values is deferred to the later **Acting Dynamically from Metadata** milestone.
