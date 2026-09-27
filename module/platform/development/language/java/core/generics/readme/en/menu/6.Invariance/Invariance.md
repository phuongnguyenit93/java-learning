# Generic Type Invariance

This is one of the most surprising parts of generics at first:

```java
Dog dog = new Dog();
Animal animal = dog; // perfectly legal

List<Dog> dogs = new ArrayList<>();
// List<Animal> animals = dogs; // why is this a compile error?
```

Looking only at `Dog extends Animal`, the rejection can feel arbitrary.

But a `List<Animal>` is allowed not only to **produce** animals but also to **accept any Animal**. If a `List<Dog>` could masquerade as `List<Animal>`, another caller could insert a `Cat` or plain `Animal` into a list that promised every element was a `Dog`.

Java therefore makes generic types **invariant by default**:

```text
Dog is a subtype of Animal
does not imply
List<Dog> is a subtype of List<Animal>
```

Wildcards selectively restore the flexibility an API needs without breaking that guarantee.

## <a id="generic-declaration-subtyping">Generic Types Still Have Inheritance</a>

“Generics are invariant” does **not** mean generic classes and interfaces lose their declared inheritance relationships.

If the declarations are related:

```java
interface Container<T> {}

class ArrayContainer<T> implements Container<T> {}
```

then the subtype relation is preserved for the **same type argument**:

```java
ArrayContainer<String> child = new ArrayContainer<>();
Container<String> parent = child; // OK
```

What invariance rejects is inventing a subtype relationship merely because the **type arguments** are related:

```text
Dog <: Animal

but

Container<Dog> </: Container<Animal>
```

Keep both ideas together:

```text
declared inheritance still works
+
type-argument inheritance does not automatically transfer through a generic type
```

## <a id="generic-invariance">Generic Invariance</a>

```java
class Animal {}
class Dog extends Animal {}

List<Dog> dogs = new ArrayList<>();
// List<Animal> animals = dogs; // compile error
```

Even though `Dog <: Animal`, Java does not infer:

```text
List<Dog> <: List<Animal>
```

If an API only needs to read animals:

```java
List<? extends Animal> animals = dogs;
```

If it needs a destination that can accept dogs:

```java
List<? super Dog> target = new ArrayList<Animal>();
```

Wildcards provide controlled **use-site variance** instead of making the generic type globally covariant.

## <a id="variance-vs-arrays">Generic Invariance vs Array Covariance</a>

Java arrays are covariant:

```java
Integer[] integers = {1, 2};
Number[] numbers = integers; // compiles
```

The runtime then has to check stores:

```java
numbers[0] = 3.14; // ArrayStoreException
```

Arrays retain their component type at runtime, so the JVM can detect the bad store.

Generics choose a different trade-off:

```java
List<Integer> integers = new ArrayList<>();
// List<Number> numbers = integers; // compile error
```

The unsafe relationship is rejected statically instead of being accepted and guarded by a runtime store check.

## <a id="variance-safety">Why Invariance Preserves Safety</a>

Imagine `List<Dog>` could be assigned to `List<Animal>`:

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs; // imagine this were legal
animals.add(new Animal());

Dog dog = dogs.get(0); // original guarantee is broken
```

The problem exists because `List` both **produces** and **consumes** elements. Global covariance would make mutation unsafe.

```text
exact generic type
→ invariant by default

? extends T
→ producer-oriented view

? super T
→ consumer-oriented view
```

Invariance is not an arbitrary restriction; it is part of how the compiler keeps mutable generic structures consistent.

Next comes a legacy compatibility mechanism that can bypass some of these guarantees: raw types.
