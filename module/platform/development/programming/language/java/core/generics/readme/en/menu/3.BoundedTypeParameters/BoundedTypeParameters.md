# Bounded Type Parameters

Generics let us say “this method works with **some type T**.” Sometimes, however, “any type” is too weak.

Suppose we want one method for several numeric types:

```java
static <T> double twice(T value) {
    // value.doubleValue(); // compile error
    return 0;
}
```

The compiler only knows that `T` is an unknown type. It cannot assume that every possible `T` has `doubleValue()`.

We need to express:

```text
"T may vary,
but T must at least be a Number"
```

That is the purpose of a **bounded type parameter**: preserve generic reuse while restricting the legal types enough to give the implementation a stronger contract.

## <a id="upper-bounded-type">Upper-Bounded Type Parameters</a>

```java
static <T extends Number> double twice(T value) {
    return value.doubleValue() * 2;
}
```

`T extends Number` means the type argument must be `Number` or one of its subtypes.

```java
twice(10);      // Integer
twice(2.5);     // Double
// twice("10"); // compile error
```

The keyword is still `extends` when the bound is an interface:

```java
static <T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

A bound simultaneously restricts callers and gives the implementation safe capabilities.

Type-parameter declarations support **upper bounds** with `extends`:

```java
<T extends Number> // legal
// <T super Integer> // illegal
```

When an API needs to describe “some supertype of Integer,” Java uses a **lower-bounded wildcard** such as `? super Integer` at the use site; the Wildcards chapter covers that mechanism.

## <a id="multiple-bounds">Multiple Bounds</a>

A type parameter may need to satisfy several contracts:

```java
static <T extends Number & Comparable<T>> T larger(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

If a class bound is present, it must come first, and there can be at most one class:

```java
<T extends SomeClass & InterfaceA & InterfaceB>
```

This is illegal:

```java
// <T extends InterfaceA & SomeClass> ...
```

Bound order also has a later compiler/runtime consequence. The **Type Erasure** chapter explains precisely why the first bound matters; for now, keep the declaration rule: when a class bound exists, it must precede the interface bounds.

## <a id="recursive-bound">Recursive / Self Bounds</a>

This is an **advanced section**. On a first pass, it is enough to understand that a bound can describe a relationship between `T` and a generic capability involving that same `T`; you do not need to design complex F-bounds yet.

A bound can refer back to the type parameter:

```java
<T extends Comparable<T>>
```

This says that `T` must be comparable to `T`.

```java
static <T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

This is often described as a recursive bound or F-bounded style. It does not create runtime recursion; it describes a **type constraint**.

Some APIs use a more flexible form such as `Comparable<? super T>` when the comparison capability may be declared on a supertype. Wildcards and PECS explain that direction later.

## <a id="bound-api-capability">Bounds Expose Safe Capabilities</a>

Without a bound:

```java
static <T> double area(T value) {
    // value.doubleValue(); // compiler cannot assume this method exists
    return 0;
}
```

With a bound:

```java
static <T extends Number> double asDouble(T value) {
    return value.doubleValue();
}
```

The compiler permits members guaranteed by the bound.

```text
bound
→ restricts the legal type-argument set
→ gives the implementation a stronger contract in return
```

Do not add bounds only to make an API appear stricter. An unnecessarily specific bound reduces reuse. Require only the capability the algorithm genuinely needs.

Bounds constrain a **named type variable**. Before moving to wildcards, one generic rule must be clear: subtype relationships between type arguments do **not** automatically transfer through a generic type. The next chapter explains invariance and why `List<Dog>` is not automatically a `List<Animal>`.
