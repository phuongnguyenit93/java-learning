# Generic Types and Generic Methods

The previous chapter introduced `T` as a type parameter. This chapter uses that idea at two different scopes:

```text
generic type
→ a class/interface owns the type parameter
→ several members share that type relationship

generic method
→ one method owns the type parameter
→ the type relationship is local to that operation
```

## <a id="generic-type-declaration">Declaring and Using Generic Types</a>

A generic class declares its type parameter after the class name:

```java
final class Box<T> {
    private T value;

    void set(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

At a use site, a concrete type argument forms a parameterized type:

```java
Box<String> textBox = new Box<>();
Box<Integer> numberBox = new Box<>();
```

A generic type is not limited to a class. An interface may also declare type parameters:

```java
interface Repository<ID, T> {
    T findById(ID id);
    void save(T value);
}
```

An implementation may keep the parameters generic or fix them to concrete types:

```java
class User {}

final class UserRepository implements Repository<Long, User> {
    @Override
    public User findById(Long id) {
        return null;
    }

    @Override
    public void save(User value) {
    }
}
```

`ID` and `T` are both type parameters of `Repository`. Another implementation could keep them generic instead of fixing them immediately to `Long` and `User`.

The key idea is not the `<T>` syntax by itself. It is **a type relationship owned by the declaration and reused consistently across members**.

## <a id="diamond-operator">Diamond Operator — &lt;&gt;</a>

When you write:

```java
Box<String> box = new Box<>();
```

the `<String>` on the left makes the variable type `Box<String>`. The empty `<>` on `new Box<>()` is informally called the **diamond operator**.

The compiler infers the **type arguments of the generic class being instantiated** from context:

```java
Box<String> box = new Box<>();
// same generic type as:
Box<String> other = new Box<String>();
```

Diamond is not the same as a raw type, and it is separate from any type parameters declared by a generic constructor:

```java
Box<String> safe = new Box<>(); // generic; String is inferred
Box<String> risky = new Box();  // raw Box -> unchecked conversion warning
```

For beginner code, the practical rule is simple: when the compiler can infer the type argument for a generic object creation, prefer `<>` rather than repeating the type argument.

### When only one method needs to be generic

`Box<T>` needs a type parameter because the **whole object** works with one type `T`. But many classes do not need to be generic at all; only **one method** may have logic that is identical across data types.

Without a generic method, that can lead to duplicated operations:

```java
static String echoString(String value) { return value; }
static Integer echoInteger(Integer value) { return value; }
```

The algorithm is unchanged; only the data type differs. There is no reason to turn the whole utility class into `Util<T>` just for this operation.

A **generic method** gives the method its own type slot:

```java
static <T> T echo(T value) {
    return value;
}
```

One method now works with `String`, `Integer`, `User`, and more, while preserving the relationship: **pass a T, get a T back**.

## <a id="generic-method-syntax">Generic Method Syntax</a>

The type-parameter list appears **before the return type**:

```java
static <T> T echo(T value) {
    return value;
}
```

Here:

- `<T>` declares the method type parameter;
- `T` in the parameter and return type refers to the same type variable;
- the method may live in either a generic or a non-generic class.

Another example:

```java
static <T> Box<T> boxOf(T value) {
    Box<T> box = new Box<>();
    box.set(value);
    return box;
}
```

Generic methods are useful when the type relationship belongs to **one operation**, not to object state.

## <a id="generic-constructor">Generic Constructors</a>

A constructor may declare its own type parameter even when the class itself is not generic:

```java
class Message {
    <T> Message(T source) {
        System.out.println(source);
    }
}
```

Here `T` belongs to the **constructor**, not to `Message`.

At the call site:

```java
new Message("java"); // T is inferred as String
new Message(123);    // T is inferred as Integer
```

The caller normally does not need to write the constructor type argument explicitly; the compiler infers it from the argument.

A generic class may also have a constructor with an independent type parameter:

```java
class Box<T> {
    <U> Box(U initialMetadata) {
        // T belongs to the class; U belongs to the constructor
    }
}
```

This is not a pattern you need every day. The important mental model is scope: a class/interface, constructor, or method may own its own type parameters.

## <a id="type-inference">Type Inference</a>

Callers usually do not need to write the method type argument explicitly:

```java
String name = echo("java");
Integer number = echo(100);
```

The compiler infers `T` from constraints such as:

- argument types;
- target type from assignment/return context;
- other constraints involved in method applicability.

An explicit type witness is available when needed:

```java
String value = Util.<String>echo("java");
```

When inference is already clear, this is usually unnecessary.

Type inference is not runtime guessing. The compiler must resolve a type satisfying the invocation constraints before bytecode is produced.

## <a id="static-generic-method">Static Generic Methods</a>

A static method is not tied to an instance, but it can still be generic when **the method declares its own type parameter**:

```java
final class Boxes {
    static <T> Box<T> of(T value) {
        Box<T> box = new Box<>();
        box.set(value);
        return box;
    }
}
```

Contrast:

```java
class Container<T> {
    // static T value;               // illegal
    static <U> U identity(U value) { // legal
        return value;
    }
}
```

`U` belongs to the static method and does not depend on the class-level `T` of any `Container` parameterization.

## <a id="generic-method-vs-type">Method vs Class Type Parameters</a>

```java
class Holder<T> {
    T current;

    <R> R echoOther(R value) {
        return value;
    }
}
```

- `T` belongs to the class and participates in the instance contract;
- `R` exists only within the declaration of `echoOther`.

A method is allowed to reuse the same type-parameter name and shadow the class parameter:

```java
class Sample<T> {
    <T> T identity(T value) {
        return value;
    }
}
```

These two `T` variables are different. The syntax is legal but confusing, so a different name is usually better.

An unconstrained `T` exposes only capabilities guaranteed by `Object`. The next chapter adds **bounds** when generic code needs a stronger contract.
