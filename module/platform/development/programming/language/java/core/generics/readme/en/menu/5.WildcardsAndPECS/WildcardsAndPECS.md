# Wildcards, PECS, and Data Direction

The previous chapter explained why `List<Dog>` is not automatically a `List<Animal>`. That rule protects type safety, but real APIs still need a way to accept **a related family of generic types** without weakening invariance.

We also know that `List<String>` and `List<Integer>` are different parameterized types. A new requirement appears:

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

Directly connecting this to invariance:

```java
List<Dog> dogs = new ArrayList<>();

List<? extends Animal> animals = dogs;
List<? super Dog> target = new ArrayList<Animal>();
```

Wildcards provide controlled **use-site variance** instead of making the generic type globally covariant.

The previous Invariance chapter already introduced the minimal `List<E>` model (`get` for reading, `add` for writing, and `size` for element count). This chapter reuses only that model and assumes no additional Collection knowledge.

## <a id="unbounded-wildcard">Unbounded Wildcard — ?</a>

```java
static int sizeOf(List<?> values) {
    return values.size();
}
```

`List<?>` means “a `List` of some specific type, but this method does not know which type.”

It is different from raw `List`:

- `List<?>` preserves generic type safety;
- raw `List` weakens or bypasses some generic checks and may produce unchecked warnings.

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

## <a id="wildcard-vs-type-parameter">Wildcard or Named Type Parameter?</a>

`?` and `<T>` both avoid hard-coding one concrete type, but they solve **different API problems**.

When an API only needs to say “this value belongs to some compatible generic type” and never needs to refer to that exact type again, a wildcard is often enough:

```java
static int sizeOf(List<?> values) {
    return values.size();
}
```

When several positions must refer to **the same type**, a named type parameter preserves that relationship:

```java
static <T> T first(List<T> values) {
    return values.get(0);
}

static <T> void copyOne(T value, List<? super T> target) {
    target.add(value);
}
```

Mental model:

```text
?
→ an unknown type argument at a use site
→ no name is needed for reuse elsewhere

<T>
→ a named type variable
→ use it when an API must preserve the same type identity across positions
```

This is not a rule that “wildcards are for reads and `<T>` is for writes.” The real question is: **does the API need to preserve and reuse one type relationship across multiple positions?**

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

### From Wildcards to PECS

Once `? extends T` and `? super T` are available, the practical problem becomes:

```text
"When should I use extends?
When should I use super?
Why?"
```

Consider a copy operation:

```java
static <T> void copy(
        List<? extends T> source,
        List<? super T> target) {
    for (T value : source) {
        target.add(value);
    }
}
```

`source` is where the method **reads T from**. `target` is where the method **writes T into**.

**PECS — Producer Extends, Consumer Super** — is a mnemonic for that direction:

```text
source PRODUCES values we read  → ? extends T
target CONSUMES values we write → ? super T
```

PECS is not a new Java language feature; it is a heuristic for understanding and designing wildcard signatures.

## <a id="pecs-rule">The PECS Rule</a>

Ask what a parameter does with values of type `T`:

```text
parameter mainly PRODUCES T for the method to read
→ ? extends T

parameter mainly CONSUMES T written by the method
→ ? super T

parameter must both read exact T and write T,
or several positions must preserve the same type relationship
→ often use an exact type or a named type parameter
```

Classic copy shape:

```java
static <T> void copy(
        List<? extends T> source,
        List<? super T> target) {
    for (T value : source) {
        target.add(value);
    }
}
```

## <a id="read-from-producer">Reading from a Producer — extends</a>

`List<? extends Animal>` may actually be:

```java
List<Animal>
List<Dog>
List<Cat>
```

The method can safely read:

```java
Animal animal = source.get(0);
```

Every possible list produces at least an `Animal`.

But it cannot safely write a `Dog`:

```java
// source.add(new Dog()); // compile error
```

The list might really be a `List<Cat>`.

“extends means read-only” is only a shorthand. Operations independent of element type, such as `clear()`, can still be legal. The important restriction is writing a **specific element value** when the exact captured type is unknown.

## <a id="write-to-consumer">Writing to a Consumer — super</a>

`List<? super Dog>` may be:

```java
List<Dog>
List<Animal>
List<Object>
```

Writing a `Dog` is safe:

```java
target.add(new Dog());
```

When reading, only this is guaranteed:

```java
Object value = target.get(0);
```

The actual list may contain other values when its true element type is `Animal` or `Object`.

## <a id="pecs-api-design">Applying PECS to API Design</a>

A narrow signature:

```java
static void moveDogs(List<Dog> source, List<Dog> target)
```

works only when both sides are exactly `List<Dog>`.

A more flexible signature:

```java
static void moveDogs(
        List<? extends Dog> source,
        List<? super Dog> target)
```

allows a source of a `Dog` subtype and a destination whose element type is `Dog` or a supertype.

The JDK uses this shape broadly; APIs such as `Collections.copy` model the source as a producer and the destination as a consumer.

Do not apply PECS mechanically:

- exact `List<T>` or a named type parameter may be clearer when the parameter must both read and write the same `T`;
- a wildcard that adds no real flexibility only adds cognitive cost;
- a named type parameter is appropriate when the return type or several inputs must relate directly to one another.

In particular, **avoid wildcard return types in public APIs** when a more specific return type can express the contract. A return such as `List<? extends Animal>` forces callers to keep carrying and reasoning about the unknown captured type. Wildcards are often most useful at **input boundaries**, where an API wants to accept a broader family of valid parameterized types.

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

Wildcards + PECS now complete the use-site flexibility story. The next chapter moves to Java's legacy-compatibility boundary: **raw types**.
