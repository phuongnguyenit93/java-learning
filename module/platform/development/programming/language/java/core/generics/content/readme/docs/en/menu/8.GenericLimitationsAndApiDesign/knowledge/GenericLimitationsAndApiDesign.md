# Generic Limitations and API Design

This chapter is primarily a **deeper second-pass topic**, not syntax you must memorize for everyday use. Beginners should know these restrictions exist and understand their common cause; they do not need every edge case memorized before using generics.

After learning `T`, wildcards, and erasure, several questions usually appear:

```java
// List<int> numbers;             // why not?
// T value = new T();             // why not?
// T[] values = new T[10];        // why not?
// Object value; value instanceof List<String> // why not?
```

If each line is learned as an isolated prohibition, generics quickly becomes a list of rules to memorize.

This chapter groups those restrictions by **cause**.

Many restrictions can be understood from the interaction of these facts, but not every language rule has exactly the same cause:

```text
generics are checked mainly at compile time
        +
type erasure removes part of generic type identity at runtime
        +
some JVM mechanisms require concrete runtime type information
```

Once those ideas are connected, restrictions around primitive type arguments, `new T()`, generic arrays, and several runtime checks become easier to reason about. Other rules, such as static type-parameter scope or `Throwable` restrictions, also depend on Java's language and type-model rules rather than erasure alone.

## <a id="no-generic-primitives">No Primitive Type Arguments</a>

These are illegal:

```java
// List<int> numbers;
// Box<double> value;
```

Type arguments must be reference types:

```java
List<Integer> numbers = new ArrayList<>();
Box<Double> value = new Box<>();
```

Autoboxing makes use convenient:

```java
numbers.add(1); // int -> Integer
```

But the wrapper is still an object, with different allocation/identity/nullability behavior and potential costs compared with a primitive. `List<Integer>` is not a primitive-specialized `int[]`.

## <a id="no-new-type-parameter">Cannot Instantiate T Directly</a>

This is illegal:

```java
static <T> T create() {
    // return new T();
}
```

After erasure, `T` alone does not identify a concrete runtime class that can be instantiated with `new T()`. In addition, declaring `<T>` does not state that every valid type argument provides any particular constructor.

Generic code must receive an **object-creation strategy from outside** rather than guess a constructor. The idea can be expressed using only concepts already learned:

```java
interface Factory<T> {
    T create();
}

static <T> T create(Factory<T> factory) {
    return factory.create();
}
```

The caller decides how to create `T`; the generic method only preserves the type relationship. Shorter lambda-based forms and `Class<T>`/Reflection-based creation belong to later modules.

## <a id="generic-array-limit">Generic Array Restrictions</a>

These array creations are illegal:

```java
// T[] values = new T[10];
// List<String>[] lists = new List<String>[10];
```

Arrays are reified and covariant: runtime knows the component type and performs store checks. A parameterized type such as `List<String>` is non-reifiable, so the JVM cannot enforce the full array component contract for it.

`List<?>[]` can be created because `List<?>` is reifiable:

```java
List<?>[] lists = new List<?>[10];
```

In practical code, after the Collection module, you will often prefer collections over generic arrays when dynamic sizing is useful and an API does not require arrays. When an internal optimization or API contract truly requires an array, any unchecked cast should be isolated behind a clear invariant.

## <a id="runtime-type-information-limit">Runtime Type-Information Limits</a>

Erasure limits which questions runtime can answer directly about `T` or a concrete generic type argument.

A type parameter cannot be used as a class literal:

```java
static <T> void inspect() {
    // Class<?> type = T.class; // compile error
}
```

With a very broad reference, runtime also cannot arbitrarily verify a concrete generic argument:

```java
Object value = new ArrayList<String>();
// value instanceof List<String> // compile error
```

Likewise, `instanceof T` is not available because `T` does not represent an independent runtime class identity after erasure:

```java
// if (value instanceof T) { } // compile error
```

But since Java 16, some parameterized `instanceof` checks are legal when the static type permits a checked cast, such as `List<Integer> → ArrayList<Integer>`. The real restriction is not “all parameterized types are forbidden with instanceof”; it is that runtime **cannot independently verify arbitrary erased concrete type arguments**.

When generic code genuinely needs a runtime type token, an API usually receives one from the caller, such as `Class<T>`, rather than trying to recover it from `T` alone.

## <a id="static-type-parameter-limit">Static Context and Class Type Parameters</a>

```java
class Box<T> {
    // static T cached; // compile error
}
```

A static member exists once for the class, while `Box<String>`, `Box<Integer>`, and other parameterizations do not become separate runtime classes with separate static state.

A static method may declare its own type parameter:

```java
class Box<T> {
    static <U> U identity(U value) {
        return value;
    }
}
```

`U` belongs to the method, not to a parameterized `Box` instance.

## <a id="erasure-signature-clash">Signature Clashes after Erasure</a>

Erasure prevents overloads that differ only in generic arguments when their erased signatures collide:

```java
// void process(List<String> values) {}
// void process(List<Integer> values) {} // name clash after erasure
```

## <a id="generic-exception-limit">Generic Exception Restrictions</a>

A generic class cannot extend `Throwable`:

```java
// class Problem<T> extends Exception {} // compile error
```

A type parameter cannot be used as a catch type:

```java
// catch (T ex) { ... }
```

However, a bounded type parameter can participate in a `throws` contract:

```java
static <E extends Exception> void run(ThrowingTask<E> task) throws E {
    task.run();
}

interface ThrowingTask<E extends Exception> {
    void run() throws E;
}
```

This can carry checked-exception type information through the compile-time API without creating a generic exception class.

## <a id="generic-api-design">Designing Generic APIs</a>

A type parameter should express a **real type relationship**, not mechanically replace every `Object`.

For example:

```java
interface Repository<ID, T> {
    T findById(ID id);
    void save(T value);
}
```

The signature tells a caller:

- which type identifies an entity;
- which type the repository returns;
- which values it accepts.

Practical guidelines:

- use a type parameter when several members must share the same type identity;
- avoid returning `Object` when the API really knows a stronger type;
- keep the number of type parameters only as large as the model requires;
- if a caller only needs “some unknown type,” a wildcard may be clearer than a named parameter that establishes no relationship;
- do not use raw types merely to shorten syntax.

A practical decision table ties the module together:

| API need | Usually appropriate tool |
| --- | --- |
| Several positions must preserve **the same type** | A named type parameter such as `<T>` |
| `T` must provide a minimum capability | A bounded type parameter such as `<T extends Number>` |
| The API only needs “some type” and does not need to name it again | Wildcard `?` |
| A parameter mainly produces `T` values | `? extends T` |
| A parameter mainly consumes `T` values | `? super T` |
| A parameter must both read and write the exact same `T` | An exact type such as `List<T>` is often clearer than a wildcard |
| Legacy integration forces a raw type | Contain it at a small boundary, validate/convert, then return to typed code |
| Code needs object creation or runtime class information | Receive a factory, `Class<T>`, or another suitable type token from the caller |

The point is not to choose the most sophisticated syntax. Start from **the type relationship the API actually needs to express**, then use the simplest mechanism that preserves that relationship and type safety.

### Generics Mental Model Summary

```text
Generic Types / Methods
→ express reusable type relationships

Bounds
→ require minimum capabilities

Invariance
→ prevents unsafe subtype assumptions between parameterized types; especially important for mutable structures

Wildcards + PECS
→ widen API boundaries by read/write direction

Raw Types
→ legacy compatibility escape hatch that can weaken safety

Type Erasure
→ implementation foundation and source of many restrictions
```

The goal of generic API design is not to use the most sophisticated syntax. It is to **express the real type relationship clearly enough that the compiler can protect both callers and implementations as early as possible**.
