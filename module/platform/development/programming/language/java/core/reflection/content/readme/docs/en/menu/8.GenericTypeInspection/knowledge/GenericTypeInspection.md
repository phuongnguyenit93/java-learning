# Generic Signature Metadata after Erasure

Once reflection can inspect classes and members, another question appears: Java uses **type erasure**, so what can runtime code still know about `List<String>`, `T extends Number`, or `? super Integer`?

The answer has two parts. The JVM generally does not carry each object's type arguments as separate runtime generic type identities, but the compiler can store **generic signature metadata** in the class file. Reflection reads that declaration metadata through `Type` and its subtypes.

This chapter assumes the learner has already covered the Generics module. Only four ideas need to be carried forward: **erasure** removes most type arguments from the execution-time type; a **type variable** is a variable such as `T`; a **bound** constrains what `T` can represent; and a **wildcard** such as `? extends X` / `? super X` describes a declaration-side type relationship. The goal here is not to reteach Generics, but to learn **how reflection represents those declarations as runtime metadata**.

The running domain is still the payment model. A natural generic field could be:

```java
class PaymentService {
    private List<PaymentRequest> recentRequests;
}
```

Reflection sees `List.class` as the raw type while the retained generic declaration can still describe `List<PaymentRequest>`.

`PaymentService` does not naturally demonstrate every generic shape. From this point, `Repository<T ...>` is therefore a **supporting specimen only** for observing `TypeVariable`, wildcards, and generic arrays without distorting the main payment model:

```java
class Repository<T extends Number & Comparable<T>> {
    List<? extends T> values;
    List<? super Integer> sinks;
    T[] buffer;
}
```

It still follows the same reflection mental model: source declarations become class metadata, and reflection reconstructs a model that tools and frameworks can inspect at runtime.

## <a id="type-interface">Type as the Generic Metadata Model</a>

`java.lang.reflect.Type` is the common interface for the type shapes returned by generic reflection. The important rule is that a `Type` is not necessarily a `Class<?>`.

The main forms are:

- `Class<?>`: a type represented directly by a runtime `Class`, such as `String`, raw `List`, `int`, or `String[]`;
- `ParameterizedType`: a declaration with type arguments, such as `List<String>`;
- `TypeVariable<?>`: a type variable such as `T` in `Repository<T>`;
- `WildcardType`: a wildcard such as `? extends T` or `? super Integer`;
- `GenericArrayType`: an array whose component type is a generic type shape that cannot be represented by `Class<?>` alone, such as `T[]`.

For example:

```java
for (Field field : Repository.class.getDeclaredFields()) {
    Type raw = field.getType();
    Type generic = field.getGenericType();

    System.out.println(field.getName());
    System.out.println("raw     = " + raw.getTypeName());
    System.out.println("generic = " + generic.getTypeName());
}
```

For `values`, `getType()` returns `List.class`, while `getGenericType()` describes `List<? extends T>`. For `buffer`, the erased raw type is `Number[]`, while the generic metadata represents `T[]` as a `GenericArrayType`.

This is why generic reflection APIs return `Type`: `Class<?>` alone cannot represent a nested generic structure.

## <a id="parameterized-type">ParameterizedType and Type Arguments</a>

`ParameterizedType` represents declarations such as `List<String>`, `Map<String, PaymentRequest>`, or `List<? extends T>`.

```java
Field field = Repository.class.getDeclaredField("values");
Type type = field.getGenericType();

if (type instanceof ParameterizedType parameterized) {
    System.out.println(parameterized.getRawType());

    for (Type argument : parameterized.getActualTypeArguments()) {
        System.out.println(argument.getTypeName());
    }
}
```

Here the structure is:

```text
raw type        → java.util.List
actual argument → ? extends T
```

`getActualTypeArguments()` may itself return a `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType`, or another type structure. Framework code therefore often walks `Type` as a tree instead of blindly casting every argument to `Class<?>`.

`ParameterizedType` also exposes `getOwnerType()` for nested/member types whose owner is parameterized. That is not central to this example, but it reinforces that a generic signature can be a multi-level structure.

## <a id="type-variable-bounds">TypeVariable and Bounds</a>

In `Repository<T extends Number & Comparable<T>>`, `T` is a `TypeVariable` declared by `Repository`.

```java
TypeVariable<Class<Repository>> variable = Repository.class.getTypeParameters()[0];

System.out.println(variable.getName()); // T

for (Type bound : variable.getBounds()) {
    System.out.println(bound.getTypeName());
}
```

The bounds reflect the declaration constraints:

```text
java.lang.Number
java.lang.Comparable<T>
```

If source code declares only `<T>`, its implicit upper bound is `Object`. A `TypeVariable` can also report the generic declaration that owns it through `getGenericDeclaration()`.

Type variables do not belong only to classes. Generic methods and constructors can declare their own variables because both `Method` and `Constructor` are `Executable` values:

```java
class Converter {
    public <R> R convert(Object source, Class<R> targetType) {
        return targetType.cast(source);
    }
}

Method convert = Converter.class.getMethod(
        "convert",
        Object.class,
        Class.class
);

TypeVariable<Method>[] methodVariables = convert.getTypeParameters();
System.out.println(methodVariables[0].getGenericDeclaration() == convert); // true
```

`getGenericDeclaration()` therefore tells framework code which declaration owns `T` or `R`, not merely the variable's name.

A common mistake is to see `T` and then search for “the real runtime class of T.” Reflection is describing the **type variable in a declaration**; it does not automatically know whether some particular object was originally created as `Repository<Integer>` or `Repository<Long>`.

## <a id="wildcard-reflection">Wildcards through Reflection</a>

Wildcards express compile-time variance relationships, and their bounds are retained in generic metadata.

For this field:

```java
List<? extends T> values;
```

the type argument is a `WildcardType` whose upper bound is `T` and which has no useful lower bound. For:

```java
List<? super Integer> sinks;
```

the wildcard has `Integer` as its lower bound, while its upper bound remains `Object`.

They can be inspected directly:

```java
ParameterizedType listType = (ParameterizedType)
        Repository.class.getDeclaredField("sinks").getGenericType();

WildcardType wildcard = (WildcardType) listType.getActualTypeArguments()[0];

System.out.println(Arrays.toString(wildcard.getUpperBounds()));
System.out.println(Arrays.toString(wildcard.getLowerBounds()));
```

Reflection exposes metadata about the **declared constraint**. It does not turn a wildcard into one concrete runtime class. A framework resolving a generic type tree must understand upper/lower bounds and continue resolving nested `Type` values as needed.

## <a id="erasure-vs-signature-metadata">Erasure versus Generic Signature Metadata</a>

Type erasure means many generic distinctions do not exist as separate type identities during execution. For example:

```java
Repository<Integer> integers = new Repository<>();
Repository<Long> longs = new Repository<>();

System.out.println(integers.getClass() == longs.getClass()); // true
```

Both objects have the same runtime `Class`: `Repository.class`. Reflection cannot inspect an arbitrary `Repository` object and always recover “this object is a `Repository<Integer>`.” That information has usually been erased.

Generic information can still survive at **declaration sites**. A field `List<PaymentRequest> requests`, a method returning `List<String>`, a superclass `BaseRepository<PaymentRequest>`, or the declaration `Repository<T extends Number>` may carry generic signature metadata. APIs such as `getGenericType()`, `getGenericReturnType()`, `getGenericParameterTypes()`, and `getGenericSuperclass()` read that metadata.

Keep these two layers separate:

```text
runtime execution type
→ primarily erased Class/JVM descriptors

generic signature metadata
→ describes generic declarations for compilers, tools, and frameworks
```

An anonymous class or explicit subclass can sometimes preserve a concrete argument in its generic superclass signature, which enables “type token” patterns. That works because **the subclass declaration contains metadata**, not because the JVM secretly remembers type arguments for every generic object.

Generic reflection should also not be treated as a replacement for compile-time generic safety. It helps serializers, dependency-injection containers, schema generators, and other frameworks understand declarations; runtime validation and behavior still need explicit design.

The next ROADMAP milestone is **Dynamic Proxies and Call Interception**: an integration use case where `Method` metadata is routed through an `InvocationHandler` to provide runtime intermediary behavior.
