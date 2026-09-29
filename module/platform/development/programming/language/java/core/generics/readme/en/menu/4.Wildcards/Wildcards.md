# Wildcards

We now know that `List<String>` and `List<Integer>` are different parameterized types. A new requirement appears:

```text
"This method does not care what exact element type the List has.
It only needs an operation that works for every List."
```

For example, a method that only needs the size:

```java
static int sizeOf(List<?> values) {
    return values.size();
}
```

There is no need to name the element type `T` because the method does not relate that type to another parameter or return value.

The **wildcard `?`** means: “there is a concrete type argument here, but this code does not need to know exactly which one.”

Wildcards make **API boundaries more flexible** while preserving generic type safety. `? extends ...` and `? super ...` further constrain where that unknown type sits in a type hierarchy.

### MINIMAL `List<E>` MENTAL MODEL USED IN THIS MODULE

The Collection module comes immediately after Generics, so this chapter does **not** teach List implementations or performance. For now, treat:

```text
List<E>
→ a container holding many E values

get(...)
→ read an E

add(E)
→ write an E

size()
→ read the number of elements
```

The wildcard, PECS, and invariance examples below rely only on this simple read/write model.

## <a id="unbounded-wildcard">Unbounded Wildcard — ?</a>

```java
static int sizeOf(List<?> values) {
    return values.size();
}
```

`List<?>` means “a `List` of some specific type, but this method does not know which type.”

It is different from raw `List`:

- `List<?>` preserves generic type safety;
- raw `List` bypasses generic checking and may produce unchecked warnings.

Elements can safely be read as `Object`:

```java
Object value = values.get(0);
```

A concrete value cannot be added because the compiler does not know the actual element type:

```java
// values.add("x"); // compile error
```

`null` is a type-system exception, but inserting `null` is rarely a useful reason to design a wildcard API.

## <a id="wildcard-vs-object">List&lt;?&gt; vs List&lt;Object&gt;</a>

This is a common source of confusion:

```java
List<?> unknown;
List<Object> objects;
```

`List<Object>` means the **exact element type is Object**. It can therefore accept strings, integers, users, and other objects:

```java
objects.add("java");
objects.add(123);
```

`List<?>` means an **exact element type exists, but this code does not know which one**. The variable may refer to `List<String>`, `List<Integer>`, `List<User>`, and so on:

```java
List<String> names = new ArrayList<>();
List<?> unknown = names; // OK
```

Because the compiler does not know the captured element type, arbitrary concrete values cannot be added:

```java
// unknown.add("java"); // compile error
```

Mental model:

```text
List<Object>
→ exact element type is Object

List<?>
→ this is a List of ONE specific type
→ that type is unknown here
```

## <a id="wildcard-placement">Where Can Wildcards Be Used?</a>

A wildcard is a **type argument at a use site**, not a way to declare a new named type parameter.

These are valid:

```java
List<?> values;
List<? extends Number> numbers;
List<? super Integer> targets;
```

But a generic class cannot be declared with a wildcard:

```java
// class Box<?> { } // compile error
```

Nor can a wildcard be used directly as the type argument of the object being created:

```java
// new ArrayList<?>(); // compile error
```

Likewise, a wildcard cannot be the direct type argument in an `extends` / `implements` declaration:

```java
// class MyList extends ArrayList<?> { } // compile error
```

Mental model:

```text
<T>
→ declares a named type variable

?
→ describes an unknown type argument at a use site
```

## <a id="extends-wildcard">Upper-Bounded Wildcard — ? extends</a>

```java
static double sum(List<? extends Number> values) {
    double total = 0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
```

The method can accept:

```java
List<Integer>
List<Double>
List<BigDecimal>
```

The element type is **some unknown subtype of `Number`**, so reading as `Number` is safe.

But these writes are not safe:

```java
// values.add(1);   // compile error
// values.add(2.5); // compile error
```

The actual list could be a `List<Double>`, so inserting an `Integer` would violate its element type.

## <a id="super-wildcard">Lower-Bounded Wildcard — ? super</a>

```java
static void addDefaults(List<? super Integer> target) {
    target.add(1);
    target.add(2);
}
```

The method can receive `List<Integer>`, `List<Number>`, or `List<Object>`.

Writing an `Integer` is safe because every possible target type can hold it.

When reading:

```java
Object value = target.get(0);
```

Only `Object` is guaranteed because the actual list may be a `List<Object>`.

## <a id="wildcard-capture">Wildcard Capture</a>

This is an **advanced section**. On a first pass, the required concepts are `?`, `? extends`, `? super`, and PECS. Wildcard capture matters mainly when the compiler rejects an operation even though an unknown type must remain internally consistent.

Sometimes the compiler needs to give the unknown wildcard type a temporary internal identity.

Public API:

```java
static void swapFirstTwo(List<?> values) {
    swapHelper(values);
}
```

Generic helper:

```java
private static <T> void swapHelper(List<T> values) {
    T first = values.get(0);
    values.set(0, values.get(1));
    values.set(1, first);
}
```

`List<?>` does not mean “a list of arbitrary `Object` values.” It has one concrete element type that is simply **unknown**. Capture gives that hidden type a consistent temporary variable so type-safe reads and writes can be related.

Useful design rule:

- use a wildcard when exact type identity does not need to be named;
- use a named type parameter when several parameters or a return type must share the same identity.

The next chapter turns wildcard direction into a practical design heuristic: **PECS**.
