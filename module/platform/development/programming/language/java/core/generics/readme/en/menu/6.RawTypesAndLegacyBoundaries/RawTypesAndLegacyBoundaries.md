# Raw Types and Legacy Boundaries

You may encounter both forms:

```java
List<String> names = new ArrayList<>();
List names = new ArrayList();
```

The second line looks like “generics with the `<String>` part removed.” Why does Java still allow it?

Because **generics were added after a large ecosystem of Java code already existed**. New generic code needed a way to keep interoperating with older libraries and source that had no type arguments.

`List`, `Box`, `Map`, and other generic type names used **without a type argument** are called **raw types**.

A raw type is a **legacy compatibility bridge**. The trade-off is weaker compile-time type protection. The goal of this chapter is therefore not to teach raw types as a normal style, but to **recognize them, understand their risks, and contain them at legacy boundaries**.

## <a id="raw-type-compatibility">Raw Types and Legacy Compatibility</a>

Given:

```java
class Box<T> {
    private T value;
    void set(T value) { this.value = value; }
    T get() { return value; }
}
```

`Box` without a type argument is the raw type:

```java
Box raw = new Box();
```

New code should normally use:

```java
Box<String> typed = new Box<>();
Box<?> unknown = typed;
```

`Box<?>` and raw `Box` are not equivalent. The wildcard says “there is a specific type argument that I do not know.” A raw type weakens or bypasses some generic checks for legacy compatibility.

## <a id="unchecked-warning">Unchecked Warnings</a>

Raw use often leaves the compiler unable to prove type safety:

```java
List raw = new ArrayList<String>();
raw.add(123); // unchecked call warning
```

The warning is not cosmetic. It marks a point where a generic guarantee is being crossed without proof.

Java has a mechanism for suppressing compiler warnings, but the annotation syntax belongs to the Annotation module. The important lesson here is: **do not hide a warning before understanding why the compiler cannot prove type safety**.

First ask:

- which legacy boundary caused the warning;
- whether the API can be parameterized instead;
- if a cast is unavoidable, what runtime condition makes it safe.

## <a id="heap-pollution">Heap Pollution</a>

This section **goes deeper into why unchecked type-safety failures happen**. A beginner should first be able to recognize raw types and understand unchecked warnings; heap-pollution details become more important around legacy APIs, generic varargs, and failures that surface far from their source.

Heap pollution occurs when a variable of a parameterized type refers to an object that does not satisfy the parameterized type's expected element relationship.

A raw type can create it:

```java
List<String> names = new ArrayList<>();
List raw = names;
raw.add(123); // unchecked

String name = names.get(0); // ClassCastException at an inserted cast
```

The failure may appear **far away from the pollution point**.

Heap pollution can also arise around generic varargs or unchecked casts. The `language-basics → Varargs` module owns full varargs semantics, while the related annotation belongs to the Annotation module. The deeper reason involves how much generic type information remains available at runtime; the Type Erasure chapter introduces that model. For now, keep the practical rule: an unchecked operation can create a state the compiler can no longer fully prove type-safe.

## <a id="raw-type-boundary">Containing Raw-Type Boundaries</a>

When a legacy API is unavoidable:

```java
static List<String> legacyNames() {
    List raw = LegacyApi.loadNames();
    List<String> result = new ArrayList<>();

    for (Object value : raw) {
        if (!(value instanceof String)) {
            throw new IllegalStateException("Unexpected value: " + value);
        }
        result.add((String) value);
    }

    return result;
}
```

This version validates each element and **copies it into a new `List<String>`**. The rest of the application no longer keeps an alias to the raw list and does not depend on legacy code avoiding later wrong-type mutations.

If performance or identity requirements force a zero-copy boundary such as:

```java
return (List<String>) raw;
```

the cast remains unchecked. That boundary then needs a stronger invariant: not only must the current contents be strings, but **no remaining raw alias may mutate the list with an incompatible value later**. If that cannot be guaranteed, copy/convert is safer.

A safer integration shape is:

```text
legacy/raw source
        ↓
small boundary
        ↓
validate / convert
        ↓
typed application code
```

If a warning is later suppressed, keep the suppression as narrow as possible and document exactly which invariant makes the unchecked operation safe.

Raw types make it clear that much of generic safety is a compile-time mechanism. The next chapter explains the compatibility mechanism underneath it: **type erasure**.
