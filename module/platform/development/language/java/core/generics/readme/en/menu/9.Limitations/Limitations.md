# Generics Limitations

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

Most of them come from the interaction of three facts:

```text
generics are checked mainly at compile time
        +
type erasure removes part of generic type identity at runtime
        +
some JVM mechanisms require concrete runtime type information
```

Once those ideas are connected, restrictions around primitive type arguments, `new T()`, generic arrays, and generic exceptions become consequences you can reason about rather than arbitrary rules.

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

After erasure, `T` alone does not identify a concrete constructor target.

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

Generic implementations normally prefer collections over generic arrays. When an internal array optimization is truly needed, any unchecked cast should be isolated behind a clear invariant.

Erasure also limits runtime checks of concrete type arguments. With a very broad reference:

```java
Object value = new ArrayList<String>();
// value instanceof List<String> // compile error
```

But since Java 16, some parameterized `instanceof` checks are legal when the static type permits a checked cast, such as `List<Integer> → ArrayList<Integer>`. Type Erasure explains this nuance; the real restriction is not “all parameterized instanceof checks are forbidden.”

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

Erasure also prevents overloads that differ only in generic arguments when their erased signatures collide:

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

### Generics Mental Model Summary

```text
Generic Types / Methods
→ express reusable type relationships

Bounds
→ require minimum capabilities

Wildcards + PECS
→ widen API boundaries by read/write direction

Invariance
→ protects mutable generic structures

Raw Types
→ legacy compatibility escape hatch that can weaken safety

Type Erasure
→ implementation foundation and source of many restrictions
```

The goal of generic API design is not to use the most sophisticated syntax. It is to **express the real type relationship clearly enough that the compiler can protect both callers and implementations as early as possible**.
