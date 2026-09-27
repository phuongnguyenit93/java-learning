# Generics and Generic Types

This module comes **before Collection**, so you do not need to understand `List`, `Set`, or `Map` yet. Start with one question:

```text
If the same logic must work with many data types,
how can we write it once while still letting the compiler check the exact type?
```

That is the problem **generics** solve.

> **Generics are the Java mechanism that lets a class, interface, constructor, or method declare a data type as a parameter.**
>
> This lets us reuse one structure or algorithm for many data types while preserving compile-time type checking.

`<T>`, wildcards, PECS, and type erasure are mechanisms and rules built around that core idea.

## <a id="generic-type-purpose">What Are Generics and Why Use Them?</a>

### THE PROBLEM: SAME LOGIC, DIFFERENT DATA TYPES

Suppose we need a box whose only job is to store one value and return it later.

Without generics, one option is a separate class for every type:

```java
final class StringBox {
    private String value;
    void set(String value) { this.value = value; }
    String get() { return value; }
}

final class IntegerBox {
    private Integer value;
    void set(Integer value) { this.value = value; }
    Integer get() { return value; }
}
```

The classes are almost identical. Adding `UserBox`, `OrderBox`, or `ProductBox` means duplicating the same logic again.

We can remove that duplication with `Object`:

```java
final class ObjectBox {
    private Object value;

    void set(Object value) {
        this.value = value;
    }

    Object get() {
        return value;
    }
}
```

But now we gain **code reuse** by giving up **specific type information**:

```java
ObjectBox box = new ObjectBox();
box.set("java");

String value = (String) box.get(); // caller must cast
```

The real problem appears when the wrong type is stored:

```java
ObjectBox box = new ObjectBox();
box.set(123);

String value = (String) box.get(); // runtime: ClassCastException
```

The compiler does not know that this box was “supposed” to contain strings. The mistake passes compilation and fails later at runtime.

### GENERICS SOLVE BOTH PROBLEMS

We replace the concrete type with a **type placeholder**:

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

Think of `T` as:

```text
Box<T>
    ↑
    a blank slot for "the type this Box stores"
```

Each use fills that slot with a real type:

```java
Box<String> textBox = new Box<>();
textBox.set("java");
String text = textBox.get();

Box<Integer> numberBox = new Box<>();
numberBox.set(100);
Integer number = numberBox.get();
```

There is only **one** `Box<T>` implementation, but it can be used with `String`, `Integer`, `User`, `Order`, and many other reference types.

The compiler still knows what each box accepts:

```java
Box<String> box = new Box<>();

box.set("java"); // OK
// box.set(123); // compile error
```

The type mistake is rejected **where the code is written**, rather than being deferred to runtime.

### SO WHY USE GENERICS?

Direct comparison:

| Approach | Reuses implementation | Compiler knows concrete type | Manual casts | Type mistakes found |
| --- | --- | --- | --- | --- |
| Separate `StringBox`, `IntegerBox`... | Poorly | Yes | No | Compile time |
| `Object` | Yes | No | Yes | Potentially runtime |
| `Box<T>` | **Yes** | **Yes** | **Usually no** | **Compile time** |

Generics give us both:

- **one reusable implementation for many data types**;
- **compile-time type safety**;
- **far fewer manual casts**;
- **signatures that explicitly describe type relationships**.

Generics do not make Java dynamically typed. They give the compiler more information to validate relationships before execution.

### WHEN SHOULD YOU THINK ABOUT GENERICS?

A common signal is:

```text
"this logic is the same;
only the data type changes"
```

You will later see this pattern throughout Java, for example:

```java
List<String>
Set<Long>
Map<String, User>
```

You do not need to understand `List`, `Set`, or `Map` yet; the Collection module follows Generics. These examples only show the recurring `Type<OtherType>` pattern.

And in your own APIs:

```java
class Box<T> { ... }
interface Repository<ID, T> { ... }
static <T> T first(List<T> values) { ... }
```

Not every class should be generic. If the logic is genuinely tied to one fixed type and there is no reusable type relationship to express, adding `<T>` only makes the API harder to read.

### ONE SENTENCE TO REMEMBER

```text
Generics
= write the logic once for many data types
+ keep enough type information for the compiler to protect you
```

Now that the purpose is clear, the rest of the module gives names and rules to different parts of this mechanism.

### HOW TO LEARN THIS MODULE: YOU DO NOT NEED EVERYTHING IN ONE PASS

Generics has two useful depth levels:

```text
PASS 1 — CORE, MUST BE USABLE

Generic Type
Generic Method
basic Bounded Types
? / ? extends / ? super
PECS
Invariance
Raw Types at the level of recognizing and avoiding unsafe use

→ goal:
read common generic signatures
write ordinary generic APIs
choose extends/super from data flow
understand what the compiler is protecting


PASS 2 — DEEP, COMPILER / RUNTIME REASONING

recursive bounds
wildcard capture
heap pollution
detailed type erasure
bridge methods
reifiable / non-reifiable types
generic array / exception restrictions

→ goal:
read harder JDK/framework code
understand unchecked warnings and runtime limitations
debug generic edge cases
```

If this is your first pass through generics, prioritize **Pass 1**. The deeper sections remain in the module for completeness, but they are not prerequisites for using everyday Java generics correctly.

Learning path:

```text
How does a class/interface receive a type as a parameter?
Generic Type
        ↓
Can one method introduce its own type variable?
Generic Method
        ↓
How can a type parameter require a minimum capability?
Bounded Type
        ↓
How can an API accept a family of generic types without knowing the exact type argument?
Wildcards
        ↓
How do read/write directions affect wildcard choice?
PECS
        ↓
Why is List<Dog> not a List<Animal>?
Invariance
        ↓
What happens when raw types bypass generic guarantees?
Raw Types
        ↓
What generic type information remains at runtime?
Type Erasure
        ↓
Which restrictions follow from erasure and non-reifiable types?
Generic Limitations
```

Main terms, introduced gradually:

```text
type parameter
→ the "type slot" declared by a generic class/interface/method

type argument
→ the real type supplied for that slot

bounded type parameter
→ restricts which real types are legal

wildcard
→ describes an unknown type argument at a use site

PECS
→ helps reason about ? extends / ? super from read/write direction

invariance
→ explains why List<Dog> is not automatically List<Animal>

type erasure
→ explains how generic information is translated for the JVM/runtime
```

## <a id="type-parameter">Type Parameters and Type Arguments</a>

In:

```java
class Box<T> { ... }
```

`T` is a **type parameter**: a type variable declared by `Box`.

In:

```java
Box<String> textBox = new Box<>();
Box<Integer> numberBox = new Box<>();
```

`String` and `Integer` are **type arguments**.

A declaration may have several type parameters:

```java
final class Pair<K, V> {
    private K key;
    private V value;
}
```

Common naming conventions:

| Name | Typical meaning |
| --- | --- |
| `T` | Type |
| `E` | Element |
| `K` | Key |
| `V` | Value |
| `R` | Result |

These are conventions only; the compiler assigns no special meaning to the letters.

A parameterized type such as `Box<String>` participates in compile-time type checking. It does not imply that the JVM creates a completely separate class definition for every type argument; Type Erasure explains that later.

A generic type is not limited to a class. An interface may also declare type parameters:

```java
interface Repository<ID, T> {
    T findById(ID id);
    void save(T value);
}
```

`ID` and `T` are both interface type parameters. An implementation may keep them generic or fix them to concrete type arguments.

## <a id="diamond-operator">Diamond Operator — &lt;&gt;</a>

When you write:

```java
Box<String> box = new Box<>();
```

the `<String>` on the left makes the variable type `Box<String>`. The empty `<>` on `new Box<>()` is informally called the **diamond operator**.

The compiler infers the constructor's type argument from context:

```java
Box<String> box = new Box<>();
// same generic type as:
Box<String> other = new Box<String>();
```

Diamond is not the same as a raw type:

```java
Box<String> safe = new Box<>(); // generic; String is inferred
Box<String> risky = new Box();  // raw Box -> unchecked conversion warning
```

For beginner code, the practical rule is simple: when the compiler can infer the type argument for a generic object creation, prefer `<>` rather than repeating the type argument.

## <a id="generic-invariance-intro">Invariance Preview</a>

Suppose:

```java
class Animal {}
class Dog extends Animal {}
```

`Dog` is a subtype of `Animal`, but:

```java
Box<Dog> dogs = new Box<>();
// Box<Animal> animals = dogs; // compile error
```

`Box<Dog>` is **not** automatically a subtype of `Box<Animal>`.

The intuition is mutation safety:

```java
Box<Animal> animals = dogs; // imagine this were legal
animals.set(new Animal());  // legal for Box<Animal>
```

The original `Box<Dog>` could now contain an `Animal` that is not a `Dog`.

This is **invariance**. Wildcards later provide controlled flexibility without losing type safety.

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

The next chapter narrows the scope: **how can one method be generic even when the surrounding class is not?**
