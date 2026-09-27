# Reflection Basics

Reflection matters when a program needs to treat **the structure of code as runtime data**. When code already knows exactly which type, field, method, or constructor it will use, ordinary Java syntax is clearer and the compiler can validate most mistakes ahead of time. Frameworks, serializers, test runners, and mapping tools often face a different problem: the concrete type or member is discovered only after the application is running.

The module uses one small model repeatedly:

~~~java
public record PaymentRequest(String orderId, long amount) {}

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

The goal of this chapter is to understand **what Reflection solves and what flexibility costs** before learning the individual APIs.

## <a id="reflection-model">What Reflection Is</a>

With an ordinary call, the structure being used is written directly in source:

~~~java
PaymentService service = new PaymentService("stripe");
String result = service.pay(
        new PaymentRequest("ORD-1", 100_000)
);
~~~

The compiler sees PaymentService, its constructor, and pay(...). A misspelled method name, wrong parameter type, or missing method becomes a compile-time error.

Before going further, lock in three terms used throughout this module:

- **metadata** is data that describes code structure, such as a class name, modifiers, fields, methods, parameter types, or annotations;
- a **reflective descriptor** is a runtime object that represents part of that structure, such as `Field`, `Method`, or `Constructor<?>`;
- a **receiver** is the concrete object on which an instance field or method operates. In `method.invoke(service, ...)`, `service` is the receiver.

Reflection does not manipulate source-code lines directly. It works through runtime metadata objects and member descriptors exposed by the JVM.

Reflection moves some of that decision to runtime. Code can obtain a runtime description of a type, discover members from metadata, and then read fields, invoke methods, or create objects through reflective descriptors.

~~~java
Class<?> type = PaymentService.class;

Method method = type.getMethod("pay", PaymentRequest.class);
Object service = type.getConstructor(String.class)
        .newInstance("stripe");

Object result = method.invoke(
        service,
        new PaymentRequest("ORD-1", 100_000)
);
~~~

Reflection is valuable because **the structure being operated on can be discovered while the program runs instead of being hard-coded into the caller**. It is not merely a longer way to call a method.

Consider a framework that creates objects from configuration:

~~~text
payment.class = com.example.PaymentService
payment.provider = stripe
~~~

Without reflection, the framework must either know PaymentService in advance and write a branch for it, or require applications to register factories/adapters through a predefined contract. Those alternatives are often excellent when the set of types is controlled. A general-purpose framework, however, cannot hard-code every application class that users may write later.

Reflection lets the framework ask runtime questions:

~~~text
Which class is this?
    ↓
Which constructors or members are available?
    ↓
What metadata do they expose?
    ↓
Can the framework apply its own rules to them?
~~~

The distinction becomes clearer when the class name genuinely comes from configuration rather than appearing as `PaymentService.class` in source:

~~~java
String className = config.get("payment.class");

Class<?> type = Class.forName(className);
Constructor<?> constructor = type.getConstructor(String.class);
Object service = constructor.newInstance("stripe");

Method pay = type.getMethod("pay", PaymentRequest.class);
Object result = pay.invoke(
        service,
        new PaymentRequest("ORD-1", 100_000)
);
~~~

This framework flow moves from **name → runtime type → member descriptors → operation** without requiring the concrete implementation to be the static type of `type`. If the binary name cannot be resolved, `Class.forName(...)` throws `ClassNotFoundException`; reflection does not guess a "close enough" class.

A useful definition is therefore: **Reflection is Java's runtime metadata and dynamic member-access mechanism**. It does not automatically understand business meaning, scan every loaded class for you, or turn Java into a dynamically typed language. The reflective caller still decides what to discover, which contracts are acceptable, and how failures are handled.

### Direct access versus runtime-discovered access

The two styles can perform the same operation, but their contracts are different:

| Direct/static access | Reflection |
| --- | --- |
| Type/member is written directly in source | Type/member may be discovered from runtime metadata |
| Compiler validates names and parameter types | Many mistakes become runtime failures |
| IDE/refactoring tools see dependencies clearly | Dependencies may hide in strings/configuration/metadata |
| Usually simplest for application code | Useful for general-purpose frameworks and tools |

When the structure is already known at compile time, ordinary Java calls are usually the better choice. Reflection becomes compelling when **not knowing the concrete structure ahead of time is itself part of the requirement**.

### Terminology map and module roadmap

The rest of the module uses these terms:

| Term | Role |
| --- | --- |
| Class<?> | Runtime description of a type known to the JVM |
| Field | Reflective descriptor for a field |
| Method | Reflective descriptor for a method |
| Constructor<?> | Reflective descriptor for a constructor |
| Member | Common metadata contract shared by Field/Method/Constructor |
| Executable | Shared base abstraction for Method and Constructor |
| get / set | Read or write state through Field |
| invoke | Call behavior through Method |
| newInstance | Create an object through Constructor |
| AccessibleObject | Common reflective access foundation for Field/Method/Constructor |
| Type and subtypes | Model for retained generic signature metadata |
| Dynamic Proxy | Runtime-generated interface implementation and interception mechanism |

Do not treat `Class`, `Field`, `Method`, `Constructor`, and `Type` as an unrelated API list. The core Reflection object model can be read as one system:

~~~text
                         runtime type
                             │
                         Class<?> ───────────────┐
                         │                       │
                         │ discovers             │ also implements Type
                         ↓                       │
                    member descriptors           │
                         │                       │
                 Member (common contract)        │
                  ┌──────┴───────────┐            │
                  │                  │            │
                Field           Executable        │
                  │             ┌────┴─────┐      │
                  │           Method   Constructor │
                  │             │          │       │
                  └──────┬──────┴──────────┘       │
                         ↓                          │
                 AccessibleObject                  │
               reflective access control           │
                                                    │
generic declaration metadata                       │
        ↓                                           │
       Type ◄───────────────────────────────────────┘
        ├─ Class<?>
        ├─ ParameterizedType
        ├─ TypeVariable
        ├─ WildcardType
        └─ GenericArrayType
~~~

This is a **role map**, not a claim that every branch is a literal Java inheritance edge. In the actual hierarchy, `Field` directly extends `AccessibleObject`; `Method` and `Constructor` extend `Executable`, and `Executable` extends `AccessibleObject`. All three member kinds implement the `Member` contract. `Class<?>` is both the main entry point for discovering members and an implementation of `Type` when generic metadata is an ordinary runtime class.

The whole module can now be read through four large questions:

~~~text
Class<?>          → what runtime type is this, and what structure does it expose?
Field/Method/
Constructor       → what concrete member exists, and how do we operate on it?
AccessibleObject  → are we allowed to perform that reflective operation?
Type              → how is a generic declaration represented in runtime metadata?
~~~

The learning path is:

~~~text
Reflection Basics
    ↓ why runtime inspection exists
Class Metadata
    ↓ how a runtime type is described
Fields
    ↓ discover/read/write state
Methods
    ↓ select and invoke behavior
Constructors
    ↓ create objects from runtime-selected constructors
Access Control
    ↓ understand language and JPMS access boundaries
Generic Type Inspection
    ↓ inspect retained generic signatures
Dynamic Invocation
    ↓ study conversion and dynamic invocation in more depth
Limitations / Risks
    ↓ evaluate safety, performance, and maintainability
Dynamic Proxy
    ↓ connect metadata with framework-style interception
~~~

## <a id="class-object-entrypoints">Class<?> as the Reflection Entry Point</a>

Reflection normally begins with a Class<?> object. A Class object is not the source code of a class; it is the runtime object that represents a type known to the JVM.

Three common entry points are:

~~~java
// 1. The type is known directly in source
Class<PaymentService> a = PaymentService.class;

// 2. An object is available, but its concrete type may not be known
PaymentService service = new PaymentService("stripe");
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

Class Metadata explores what the runtime knows about the type itself. The Field, Method, and Constructor chapters then focus on each member kind.

## <a id="reflection-use-cases">Where Reflection Is Useful</a>

Reflection is strongest in infrastructure code that must work with **many user-defined or extension-defined types**.

A dependency injection container may select constructors and inject dependencies. A serializer may inspect fields or properties to convert objects to JSON. An ORM may inspect entity metadata and map rows. A test runner may discover annotated test methods. A routing framework may inspect metadata and register handlers. Debugging, validation, and object-mapping tools often follow the same pattern: the target structure becomes input to a generic algorithm.

A tiny mapper can demonstrate the idea without knowing the concrete class:

~~~java
static Map<String, Object> snapshot(Object target)
        throws IllegalAccessException {
    Map<String, Object> values = new LinkedHashMap<>();

    for (Field field : target.getClass().getDeclaredFields()) {
        if (Modifier.isPublic(field.getModifiers())) {
            values.put(field.getName(), field.get(target));
        }
    }
    return values;
}
~~~

The mapper implements **one algorithm over metadata** instead of separate branches for PaymentService, Customer, Order, and every future model.

Reflection is not the only extensibility technique. Interfaces, factories, registries, ServiceLoader, generated code, and annotation processing can provide clearer or more static contracts. Reflection is justified when runtime discovery provides real value, especially when a framework cannot require every application model to hand-write an adapter.

## <a id="reflection-cost-boundary">Costs and Usage Boundaries</a>

Reflection gains flexibility by moving decisions from compile time to runtime. The first cost is therefore **reduced compile-time safety**.

~~~java
// Compiler-checked
service.pay(request);

// A typo is discovered only at runtime
service.getClass().getMethod("paay", PaymentRequest.class);
~~~

Reflective code must also handle missing members, wrong receivers, wrong arguments, denied access, and exceptions thrown by the target itself. Later chapters distinguish these failure modes precisely.

The second cost is hidden coupling. IDEs can find references to service.pay(...), while a string such as "pay" in configuration or framework metadata may be invisible to ordinary refactoring tools. Renames therefore need stronger contracts and tests.

The third cost is encapsulation pressure. Reflection still performs access checks. Some APIs can attempt to change reflective accessibility, while JPMS introduces strong-encapsulation boundaries. Access Control covers those rules. The presence of Reflection does not make private meaningless.

The fourth cost is runtime overhead. Metadata lookup and Method.invoke/Field access are generally more expensive than direct calls, although JVMs and frameworks often cache or optimize reflective structures. This cost may be irrelevant during one-time bootstrap and significant inside a hot path. Limitations / Risks discusses that trade-off in depth.

A practical decision rule is:

~~~text
Type/member is known at compile time
→ prefer direct calls, interfaces, polymorphism, or factories

The real structure is only known at runtime
and discovering it is part of the requirement
→ Reflection may be appropriate
~~~

When reflection is used, keep the dynamic area narrow: validate metadata early, cache descriptors when reused, translate runtime failures into meaningful framework/domain errors, and keep ordinary business code as type-safe as possible.

The next question is now concrete: **what metadata does a Class<?> actually expose about a runtime type?**
