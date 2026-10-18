# What Generics Are and Why They Exist

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
Invariance
? / ? extends / ? super
PECS
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

Learning path, aligned with the eight ROADMAP milestones:

```text
What problem do generics solve, and why do they exist?
What Generics Are and Why They Exist
        ↓
When does a type parameter belong to a type versus one method/constructor?
Generic Types and Generic Methods
        ↓
How can a type parameter require a minimum capability?
Bounded Type Parameters
        ↓
Why is List<Dog> not a List<Animal>?
Generic Invariance and Subtyping
        ↓
How can an API accept compatible generic families and choose extends/super from data direction?
Wildcards, PECS, and Data Direction
        ↓
What happens when raw types bypass generic guarantees?
Raw Types and Legacy Boundaries
        ↓
What generic information guides compilation, and what remains available at runtime?
Type Erasure and Runtime Metadata
        ↓
Which restrictions and trade-offs shape practical API design?
Generic Limitations and API Design
```

Main terms, introduced gradually:

```text
type parameter
→ the "type slot" declared by a generic class/interface/method/constructor

type argument
→ the real type supplied for that slot

bounded type parameter
→ restricts which real types are legal

invariance
→ explains why List<Dog> is not automatically List<Animal>

wildcard
→ describes an unknown type argument at a use site

PECS
→ helps reason about ? extends / ? super from read/write direction

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

The next chapter moves from **“what are generics?”** to **“how do generic types and generic methods get declared and used?”**
